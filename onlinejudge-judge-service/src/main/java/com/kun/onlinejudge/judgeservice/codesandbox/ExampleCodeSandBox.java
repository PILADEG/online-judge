package com.kun.onlinejudge.judgeservice.codesandbox;

import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.enums.JudgeInfoMessageEnum;
import com.kun.onlinejudge.model.judge.JudgeCase;
import com.kun.onlinejudge.model.judge.JudgeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 示例代码沙箱（dev/联调用，无需远程 Docker 沙箱）。
 *
 * <p>原实现直接返回 null，导致 JudgeServiceImpl 在组装 JudgeContext 时 NPE、
 * 提交永远停在 RUNNING 且无结果回写（Task J-2 判题闭环验收暴露）。
 * 本沙箱在本地临时目录内用 JDK 编译并逐个用例运行提交的 Java 代码，
 * 捕获 stdout / 退出码 / 编译诊断，产出一个可被 StandardJudge 消费的 ExecuteResponse：
 * 成功运行（status 为空）→ 交给模板逐字比对 outputCase；
 * 编译/运行/超时 → 返回对应 status，由模板统一回写 FAILED。
 * 仅用于 dev，生产走 DockerCodeSandBox。
 */
@Slf4j
@Component
public class ExampleCodeSandBox implements CodeSandBox {

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("public\\s+class\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    @Override
    public ExecuteResponse doExecute(ExecuteMessage executeMessage) {
        log.info("example sandbox execute start, language={}", executeMessage == null ? null : executeMessage.getLanguage());
        if (executeMessage == null) {
            return systemError("example sandbox: executeMessage 为空");
        }
        try {
            String language = executeMessage.getLanguage();
            if (language == null || !"java".equals(language)) {
                return systemError("example sandbox 仅支持 java，实际 language=" + language);
            }
            return doJava(executeMessage);
        } catch (Exception e) {
            log.error("example sandbox execute error", e);
            return systemError("example sandbox error: " + e.getMessage());
        }
    }

    /**
     * 本地编译并逐用例运行 Java，返回可判定的 ExecuteResponse。
     */
    private ExecuteResponse doJava(ExecuteMessage message) throws IOException {
        String code = message.getCode();
        List<JudgeCase> judgeCases = message.getJudgeCases();
        if (code == null || code.trim().isEmpty()) {
            return systemError("example sandbox: 代码为空");
        }
        if (judgeCases == null || judgeCases.isEmpty()) {
            return systemError("example sandbox: 判题用例为空");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return systemError("example sandbox: 当前 JVM 非 JDK，无可用编译器 javac");
        }
        Path workDir = Files.createTempDirectory("oj-example-sandbox-");
        try {
            String className = resolveClassName(code);
            Path sourceFile = workDir.resolve(className + ".java");
            Files.write(sourceFile, code.getBytes(StandardCharsets.UTF_8));

            // 1. 编译
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                List<String> options = Arrays.asList(
                        "-encoding", "UTF-8",
                        "-d", workDir.toAbsolutePath().toString());
                Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjects(sourceFile.toFile());
                boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
                if (!compiled) {
                    log.info("compile error diagnostics:{}", diagnostics.getDiagnostics());
                    return ExecuteResponse.builder()
                            .status(JudgeInfoMessageEnum.COMPILE_ERROR.getValue())
                            .errorMessage(collectDiagnostics(diagnostics))
                            .time(0L)
                            .memory(0L)
                            .build();
                }
            }

            // 2. 逐用例运行
            long timeoutMs = resolveTimeout(message.getJudgeConfig());
            List<String> outputList = new ArrayList<>();
            long totalTime = 0L;
            for (JudgeCase judgeCase : judgeCases) {
                RunResult result = runOnce(workDir.toFile(), className, judgeCase.getInputCase(), timeoutMs);
                if (result.timedOut) {
                    return ExecuteResponse.builder()
                            .status(JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED.getValue())
                            .errorMessage("执行超时(" + timeoutMs + "ms)")
                            .time(result.elapsedMs)
                            .memory(0L)
                            .build();
                }
                if (result.exitCode != 0) {
                    return ExecuteResponse.builder()
                            .status(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue())
                            .errorMessage(result.stderr)
                            .time(result.elapsedMs)
                            .memory(0L)
                            .build();
                }
                totalTime += result.elapsedMs;
                outputList.add(result.stdout);
            }
            log.info("example sandbox run ok, outputList={}, time={}ms", outputList, totalTime);
            return ExecuteResponse.builder()
                    .time(totalTime)
                    .memory(0L)
                    .outputList(outputList)
                    .build();
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * 编译通过后用本地 JDK 的 java 运行一次（class 已在 workDir 内）。
     */
    private RunResult runOnce(File workDir, String className, String input, long timeoutMs) {
        Process process = null;
        long start = System.nanoTime();
        try {
            ProcessBuilder builder = new ProcessBuilder(javaBin(), "-cp", workDir.getAbsolutePath(), className);
            builder.directory(workDir);
            final Process started = builder.start();
            process = started;

            final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            Thread outThread = new Thread(() -> pump(started.getInputStream(), outBuf));
            Thread errThread = new Thread(() -> pump(started.getErrorStream(), errBuf));
            outThread.setDaemon(true);
            errThread.setDaemon(true);
            outThread.start();
            errThread.start();

            // 写入该用例的 stdin 后关闭，子进程读 stdin 即获得输入并可 EOF 结束
            try (OutputStream stdin = process.getOutputStream()) {
                if (input != null && !input.isEmpty()) {
                    stdin.write(input.getBytes(StandardCharsets.UTF_8));
                    stdin.flush();
                }
            }

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                joinSilently(outThread);
                joinSilently(errThread);
                return new RunResult(-1, true, decode(outBuf), decode(errBuf), elapsedMs);
            }
            joinSilently(outThread);
            joinSilently(errThread);
            return new RunResult(process.exitValue(), false,
                    decode(outBuf), decode(errBuf), elapsedMs);
        } catch (IOException | InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            return new RunResult(-2, false, "", "启动/运行失败: " + e.getMessage(), elapsedMs);
        }
    }

    private static void pump(java.io.InputStream in, ByteArrayOutputStream buf) {
        byte[] buffer = new byte[4096];
        int len;
        try {
            while ((len = in.read(buffer)) != -1) {
                buf.write(buffer, 0, len);
            }
        } catch (IOException ignored) {
            // 子进程被强杀等场景，忽略读流中断
        }
    }

    private static void joinSilently(Thread thread) {
        try {
            thread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Charset charset() {
        return Charset.defaultCharset();
    }

    private static String decode(ByteArrayOutputStream buf) {
        return new String(buf.toByteArray(), charset());
    }

    private static String javaBin() {
        String home = System.getProperty("java.home");
        String exec = File.separatorChar == '\\' ? "java.exe" : "java";
        return home + File.separator + "bin" + File.separator + exec;
    }

    private static long resolveTimeout(JudgeConfig judgeConfig) {
        long timeout = 3000L;
        if (judgeConfig != null && judgeConfig.getTimeLimit() != null && judgeConfig.getTimeLimit() > 0) {
            timeout = judgeConfig.getTimeLimit();
        }
        // 示例沙箱兜底，避免子进程卡死整个消费线程
        return Math.min(timeout, 20000L);
    }

    private static String resolveClassName(String code) {
        Matcher matcher = PUBLIC_CLASS.matcher(code);
        return matcher.find() ? matcher.group(1) : "Main";
    }

    private static String collectDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder();
        List<Diagnostic<? extends JavaFileObject>> list = diagnostics.getDiagnostics();
        int count = Math.min(list.size(), 5);
        for (int i = 0; i < count; i++) {
            Diagnostic<? extends JavaFileObject> diagnostic = list.get(i);
            builder.append(diagnostic.getMessage(Locale.ROOT)).append('\n');
        }
        return builder.toString();
    }

    private static ExecuteResponse systemError(String errorMessage) {
        return ExecuteResponse.builder()
                .status(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue())
                .errorMessage(errorMessage)
                .time(0L)
                .memory(0L)
                .build();
    }

    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 忽略清理失败（临时目录）
                }
            });
        } catch (IOException ignored) {
            // 忽略清理失败（临时目录）
        }
    }

    private static final class RunResult {
        private final int exitCode;
        private final boolean timedOut;
        private final String stdout;
        private final String stderr;
        private final long elapsedMs;

        private RunResult(int exitCode, boolean timedOut, String stdout, String stderr, long elapsedMs) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.stdout = stdout;
            this.stderr = stderr;
            this.elapsedMs = elapsedMs;
        }
    }
}
