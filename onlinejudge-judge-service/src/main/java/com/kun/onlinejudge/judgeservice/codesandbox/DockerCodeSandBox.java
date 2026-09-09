package com.kun.onlinejudge.judgeservice.codesandbox;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.kun.onlinejudge.model.codesandbox.ExecuteMessage;
import com.kun.onlinejudge.model.codesandbox.ExecuteResponse;
import com.kun.onlinejudge.model.enums.JudgeInfoMessageEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * 远程 Docker 代码沙箱（通过 HTTP 调用虚拟机上的沙箱服务）
 */
@Slf4j
@Component
public class DockerCodeSandBox implements CodeSandBox {

    @Value("${codesandbox.url}")
    private String sandboxUrl;

    @Value("${codesandbox.auth-secret}")
    private String authSecret;

    private static final String AUTH = "auth";

    @Override
    public ExecuteResponse doExecute(ExecuteMessage executeMessage) {
        log.info("调用远程沙箱，请求：{}", executeMessage);
        String secretKey = DigestUtil.md5Hex(authSecret);
        HttpResponse response = null;
        try {
            response = HttpRequest.post(sandboxUrl)
                    .header("Content-Type", "application/json")
                    .header(AUTH, secretKey)
                    .body(JSONUtil.toJsonStr(executeMessage))
                    .timeout(10000)
                    .execute();
            log.info("远程沙箱响应状态码：{}", response.getStatus());
            String responseBody = response.body();
            log.info("远程沙箱响应：{}", responseBody);
            return JSONUtil.toBean(responseBody, ExecuteResponse.class);
        } catch (Exception e) {
            log.error("调用远程沙箱失败", e);
            return ExecuteResponse.builder()
                    .status(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue())
                    .errorMessage("调用远程沙箱失败: "+e.getMessage())
                    .build();
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
