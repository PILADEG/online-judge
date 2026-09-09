# Service 迁移：judge-service（判题域 + MQ 消费端）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把判题域迁成可独立运行的 `onlinejudge-judge-service`（纯后台、无 HTTP）：消费 Rabbit `code-queue` → `JudgeService.doJudge`（乐观锁置 RUNNING → 取题目用例 → 调代码沙箱 → 逐用例比对 → `JudgeUtils` 回写 `question`/`question_submit`）。同库直连 question/question_submit。使 submit→MQ→judge 判题闭环可跑。

**Architecture:** judge-service = 后台消费者：`@RabbitListener(code-queue)` manual ack → 反序列化 `QuestionSubmit` → 同库（`onlinejudge`）直连两张表。判题过程复用单体 codesandbox/judgement/template 代码，仅改包与依赖归位。无 controller、不进网关。用 `spring.main.web-application-type: none`（即便依赖链带入 starter-web 也不起 web 容器）。

**Tech Stack:** Java8、Spring Boot 2.6.13、MyBatis-Plus 3.5.2、MySQL、Spring AMQP(manual ack)、Hutool。

## Global Constraints（本段必须遵守）

- 版本沿用聚合 pom；不改父 pom。动 `onlinejudge-judge-service`；不改 model/common/service-client/user/question/submit/gateway。
- judge-service 根包 `com.kun.onlinejudge.judgeservice`；**删除**冒烟占位（`config/RabbitSmokeConfig`、`listener/SmokeListener`、旧 `OnlinejudgeJudgeServiceApplication`），重建。
- 依赖：新增 `onlinejudge-common`(带 model)、`mybatis-plus-boot-starter`、`mysql-connector-java`(runtime)；保留 `spring-boot-starter`+`amqp`+nacos-discovery+lombok。web starter 若经 common 带入无妨（`web-application-type: none`）。
- 主类只扫自身包（不扫 common 的 @Component——仅当库用；不扫描 serviceclient）。
- Mapper：judge 需 query/update question 与 question_submit → 本服务自持 `judgeservice.mapper.{QuestionMapper,QuestionSubmitMapper}`（内容同单体 BaseMapper 版，改包）。实现在 `...judgeservice.{codesandbox,judgement,judgement.strategy,judgement.template,judgement.template.Java,utils,messagelist,config,mapper}` 包。
- Rabbit：consumer 用 `RabbitConstant.QUEUE`；监听 manual ack；拓扑 config 用 `RabbitConstant`（与 submit 同源，幂等双声明）。yml `spring.rabbitmq.listener.simple.acknowledge-mode: manual`。
- 判题/沙箱：dev 用 `codesandbox.type: example`（不真连 Docker 沙箱）；judgement 类对 model/codesandbox、enums 的引用均来自 model。
- `judgement/test.java`（debug controller）不迁。wx/cos 不涉。
- context 无关（无 servlet）；端口不暴露（不配 server.port 亦可；配了也不起 web）。
- 提交信息 `feat(judge)/refactor(judge)`。

---

### Task J-0: 依赖/配置就位 + 清冒烟

**Files:**
- Modify: `onlinejudge-judge-service/pom.xml`（+ onlinejudge-common、mybatis-plus-boot-starter、mysql-connector-java(runtime)）
- Create: `.../judgeservice/config/MyBatisPlusConfig.java`、`.../judgeservice/config/RabbitConfig.java`
- Overwrite: `onlinejudge-judge-service/src/main/resources/application.yml`
- Delete: `.../judgeservice/config/RabbitSmokeConfig.java`、`.../judgeservice/listener/SmokeListener.java`、`.../judgeservice/OnlinejudgeJudgeServiceApplication.java`

**Interfaces:**
- Produces: judge 依赖集齐；MP/Rabbit 拓扑配置（RabbitConstant 同源）；真实 yml（datasource/rabbit manual/web none/nacos/codesandbox example）。主类 Task J-1 重建。

- [ ] **Step 1: pom 追加**

在 `onlinejudge-judge-service/pom.xml` 的 `<dependencies>` 内追加：

```xml
        <dependency>
            <groupId>com.kun</groupId>
            <artifactId>onlinejudge-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: config/MyBatisPlusConfig + config/RabbitConfig**

MyBatisPlusConfig 同 submit/question（PaginationInnerInterceptor(DbType.MYSQL)，包 `...judgeservice.config`）。
RabbitConfig：类同 submit 的 config/RabbitConfig（codeExchange/codeQueue/codeBinding 引用 RabbitConstant），包 `...judgeservice.config`。

- [ ] **Step 3: 覆盖 application.yml**

`onlinejudge-judge-service/src/main/resources/application.yml`：

```yaml
spring:
  main:
    web-application-type: none
  application:
    name: onlinejudge-judge-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/onlinejudge
    username: root
    password: 123456789
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        acknowledge-mode: manual
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: false
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      logic-delete-field: isDelete
      logic-delete-value: 1
      logic-not-delete-value: 0
codesandbox:
  type: example
  url: http://127.0.0.1:8201/docker/
  auth-secret: online_judge_project_secret_key
```

- [ ] **Step 4: 删冒烟并编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
rm -f onlinejudge-judge-service/src/main/java/com/kun/onlinejudge/judgeservice/config/RabbitSmokeConfig.java \
      onlinejudge-judge-service/src/main/java/com/kun/onlinejudge/judgeservice/listener/SmokeListener.java \
      onlinejudge-judge-service/src/main/java/com/kun/onlinejudge/judgeservice/OnlinejudgeJudgeServiceApplication.java
mvn -pl onlinejudge-judge-service -am install -DskipTests -Dspring-boot.repackage.skip=true 2>&1 | tail -8
```
Expected: BUILD SUCCESS（common 新依赖+空壳编译）。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "build(judge): prep deps/mq/config; drop smoke placeholders"
```

---

### Task J-1: 迁移判题域（codesandbox/judgement/JudgeUtils/mappers + consumer）

**Files:**
- Move(逐字，package `...judgeservice`): 单体下列目录/文件 →
  `codesandbox/{CodeSandBox,CodeSandBoxFactory,CodeSandBoxProxy,DockerCodeSandBox,ExampleCodeSandBox}.java` → `.../judgeservice/codesandbox/`
  `judgement/{JudgeService,JudgeServiceImpl,JudgeStrategyFactory}.java`、`judgement/strategy/{JudgeStrategy,JavaJudge}.java`、`judgement/template/{JudgeTemplate,StandardJudge}.java`、`judgement/template/Java/JavaStandardJudge.java` → `.../judgeservice/judgement/`(+`.strategy`/`.template`/`.template.Java`)
  `utils/JudgeUtils.java` → `.../judgeservice/utils/`
  `messagelist/RabbitMqConsumer.java` → `.../judgeservice/messagelist/`（改造，见下）
- Create(本服务): `.../judgeservice/mapper/{QuestionMapper,QuestionSubmitMapper}.java`、`.../judgeservice/config/RabbitMqListenerConfig.java`(如需显式容器/序列化，可并入 RabbitConfig)、`.../judgeservice/OnlinejudgeJudgeServiceApplication.java`
- 不迁：`judgement/test.java`、`messagelist/RabbitMqConfig`（拓扑由本服务 config/RabbitConfig 提供）、controller/service(用户/题目/提交域) 全部。

**Interfaces:**
- Produces: judge 独立判题：消费 code-queue → doJudge → 回写两表。对外无 HTTP。

**迁移与改造要点**
1. `judgeservice.mapper.QuestionMapper/QuestionSubmitMapper`：`extends BaseMapper<Question>/<QuestionSubmit>`，entity 用 `model.entity.*`；无 XML。
2. codesandbox/judgement 各文件：package 前缀从 `com.kun.onlinejudge.` → `com.kun.onlinejudge.judgeservice.`（目录结构照搬）；跨包 import 同步：凡 import `com.kun.onlinejudge.codesandbox.*`→`...judgeservice.codesandbox.*`；`com.kun.onlinejudge.judgement.*`→`...judgeservice.judgement.*`；`utils.JudgeUtils`→`...judgeservice.utils.JudgeUtils`；`mapper.QuestionMapper/QuestionSubmitMapper`→`...judgeservice.mapper.*`；`common.ErrorCode` 等已在 model.result（A 段下沉）→ `com.kun.onlinejudge.model.result.ErrorCode`；`common.ResultUtils/BaseResponse`→model.result（若引用）；`common`/`exception`/`constant` 等仍在 common 原包则不变；`model.*` 不变；沙箱 HTTP 客户端/枚举依赖 model.codesandbox / model.enums 不变。
3. `JudgeServiceImpl`/`JudgeUtils` 若 `@Transactional` 或注入 mappers，bean 由本服务 MP + datasource 提供；逻辑删除与 map-underscore=false 与单体一致。
4. `RabbitMqConsumer`：改到 `...judgeservice.messagelist`；`@RabbitListener(queues = RabbitConstant.QUEUE)`（不再字面量）；反序列化 QuestionSubmit（单体用 JSONUtil），manual ack：成功 `basicAck`、异常 `basicNack(requeue=false)`（保留防毒消息语义）；调 `judgeService.doJudge`。引用 `JudgeService`（同包 judgeservice.judgement）。
5. 主类重建：`@SpringBootApplication`（仅扫自身包 `com.kun.onlinejudge.judgeservice`，不扫 common/serviceclient）+ `@MapperScan("com.kun.onlinejudge.judgeservice.mapper")`。main 用 `SpringApplication.run`（web-application-type none 已在 yml，不起 tomcat）。
6. `mvn -pl onlinejudge-judge-service -am install -DskipTests` BUILD SUCCESS（可能有逐字代码对已变库的编译差异——按 import 归位规则最小修正并报告；如 DockerCodeSandBox 有 `@Value codesandbox.*`，yml 已给）。
7. 提交 `feat(judge): migrate judging domain (codesandbox/judgement/JudgeUtils) + MQ consumer`。

---

### Task J-2: 判题闭环验收（submit→MQ→judge→DB 回写）

**Files:** 无需新增（如有 bug 修复另提交）。

**验收前置**：MySQL/Redis/Nacos/Rabbit 就绪；**user(8101)+question(8102)+submit(8103)+judge** 都运行（submit 发的消息 judge 消费判题）；判题 dev 用 example 沙箱。

- [ ] **Step 1: 编译并先后台启动 4 服务**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -DskipTests install 2>&1 | tail -4
# java -jar 依次起 user/question/submit/judge（judge 日志重点看），记 PID，轮询就绪
```

- [ ] **Step 2: 验收用例（curl 记录原样输出；身份头模拟网关注入）**
1. 准备：register 用户 uid（可置 admin），admin 建题 qid（judge 需该题 judgeCases/judgeConfig 合法——example 沙箱下仍会走比对，参考单体 dev）。
2. 提交：`POST :8103/api/question_submit/add` body `{"questionId":$qid,"language":"java","code":"...编译可过的简单 Java（见下）..."}`，带 X-User-Id/Role:user → code 0 取 sid。
3. 轮询判题：`GET :8103/api/question_submit/get/vo?id=$sid`（uid/user 头）数秒一次 → 最终 status 变为 2(成功) 或 3(失败) 且 judgeInfo 有内容（**证明 submit→MQ→judge→DB 回写闭环**）；DB 中该 question 的 `submitNum` +1。
   - 提供一段能编译/运行的 Java 代码（example 沙箱逐字比对 outputCase，用 `System.out.println` 输出期望值；参考 judgeCases 的 output 设计输入，可用单个用例避免复杂）。
4. judge 日志出现"doJudge/判题成功/结果回写"类日志（记录关键行）。
5.（对照）再次提交一个**注定错**的代码（如输出与期望不符或编译错误）→ 最终 status 3(失败/FAILED) 且 judgeInfo 含判题信息（可选）。
完成后 kill 4 进程；git status clean。

- [ ] **Step 3: 提交（如有修复）**

```bash
git add -A
git commit -m "fix(judge): <摘要>"
```

---

## Self-Review 结论（作者已自查）

- judge-service 是最后一个业务服务：判题域代码（codesandbox/judgement/template/JudgeUtils）整体迁入并归位 import；MQ 消费端接 RabbitConstant；同库直连两张表不变；无 HTTP/无网关。
- 闭环验收 = 提交→判题→回写 status/judgeInfo + submitNum 递增，即整个核心 OJ 判题链路的端到端验证。
- 遗留：submit E2E 产生的 1 条滞留 code-queue 消息会被本服务消费判题（正好验证积压消费）。
