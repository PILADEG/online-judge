# Service 迁移：submit-service（提交域 + 判题 MQ Producer）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把提交域迁成可独立运行的 `onlinejudge-submit-service`：对外 `/api/question_submit/**`（add/delete/update/admin/list/vo/my/list/vo/get/get/vo）+ 提交即写 DB(WAITING) 并经 RabbitMQ 发 `code-queue` 触发判题。鉴权=身份头；校验题目存在改 Feign `QuestionServiceClient`；VO 拼 user 改 Feign `UserServiceClient`；MQ 交换机/队列/路由键常量收进 common `RabbitConstant`（submit 与后续 judge 同源，防漂移）。

**Architecture:** submit-service 只持 `question_submit` 表（单库直连），判题链路解耦保持不变（DB 落 WAITING → MQ Producer）。作为 Feign 消费方调 question-service(校验/标题) 与 user-service(user 拼装)。同前：common IdentityHeaderFilter+AuthCheck、context-path=/api、本地 config。

**Tech Stack:** Java8、Spring Boot 2.6.13、MyBatis-Plus 3.5.2、MySQL、OpenFeign、Spring AMQP、Hutool。

## Global Constraints（本段必须遵守）

- 版本沿用聚合 pom；不改父 pom。动 `onlinejudge-submit-service` + `onlinejudge-common`(新增 `constant.RabbitConstant`)。不改 model/service-client/question/user/gateway。
- submit-service 根包 `com.kun.onlinejudge.submitservice`；删除 SB 冒烟占位（`PingController`、`SmokeSendController`、`RabbitSmokeConfig`），重建主类；gateway 的 `/api/submit/**` 现指向冒烟 submit 服务，迁完即指向真实 submit。
- 依赖：service-client(+common+model)、web、nacos、loadbalancer、openfeign、amqp、mybatis-plus-boot-starter、mysql(runtime)。
- 鉴权：所有 `/api/question_submit/**` 由网关注入身份头；add/delete/get 取当前用户 `UserContext.getUserId()`；owner 判定 `submit.userId.equals(uid)`；admin 判定 `UserContext.getUserRole()==ADMIN`（常量 `UserConstant.ADMIN_ROLE`）；`@AuthCheck(ADMIN)` 用于 update/list/page。不再注入 UserService；不再用 HttpServletRequest 取登录（去参数）。
- 题目存在校验：`QuestionServiceClient.getQuestionVOById(questionId).getData()==null → NOT_FOUND`。user 拼装：单条 `UserServiceClient.getUserVOById`、分页 `listUserVOByIds`（null-safe / code!=0 抛 SYSTEM_ERROR 参照 question-service 实现）。
- MQ：common 新增 `RabbitConstant{EXCHANGE=code-exchange; QUEUE=code-queue; ROUTING_KEY=code.routing.key}`；submit 的 producer 与拓扑 config 引用之；删除冒烟 RabbitSmokeConfig。消息负载不变：QuestionSubmit 实体 JSON。
- context-path=/api、端口 8103；数据源 `onlinejudge`（同 B1/Q）。rabbitmq yml 同冒烟（guest/guest、manual ack 无需 listener 配置——submit 仅发不消费）。
- 提交信息 `feat(submit)/refactor(submit)`。

---

### Task S-0: 依赖/常量/配置就位 + 清冒烟

**Files:**
- Create: `onlinejudge-common/src/main/java/com/kun/onlinejudge/constant/RabbitConstant.java`
- Modify: `onlinejudge-submit-service/pom.xml`（+ mybatis-plus-boot-starter、mysql-connector-java(runtime)）
- Create: `.../submitservice/config/MyBatisPlusConfig.java`、`.../submitservice/config/RabbitConfig.java`
- Overwrite: `onlinejudge-submit-service/src/main/resources/application.yml`
- Delete: `.../submitservice/controller/PingController.java`、`.../submitservice/controller/SmokeSendController.java`、`.../submitservice/config/RabbitSmokeConfig.java`、`.../submitservice/OnlinejudgeSubmitServiceApplication.java`

**Interfaces:**
- Produces: `RabbitConstant`（common）；submit 依赖/MP/Rabbit 拓扑配置；真实 yml。rabbit 常量被 submit 与后续 judge 共用。

- [ ] **Step 1: common 新增 RabbitConstant**

`onlinejudge-common/src/main/java/com/kun/onlinejudge/constant/RabbitConstant.java`：

```java
package com.kun.onlinejudge.constant;

/**
 * RabbitMQ 拓扑常量（submit producer 与 judge consumer 同源）
 */
public interface RabbitConstant {

    String EXCHANGE = "code-exchange";
    String QUEUE = "code-queue";
    String ROUTING_KEY = "code.routing.key";
}
```

- [ ] **Step 2: submit pom 追加**

在 `onlinejudge-submit-service/pom.xml` 的 `<dependencies>` 内追加：

```xml
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

- [ ] **Step 3: MyBatisPlusConfig / RabbitConfig**

`.../submitservice/config/MyBatisPlusConfig.java`：与 question-service 同款（分页 PaginationInnerInterceptor(DbType.MYSQL)）。

`.../submitservice/config/RabbitConfig.java`：

```java
package com.kun.onlinejudge.submitservice.config;

import com.kun.onlinejudge.constant.RabbitConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(RabbitConstant.EXCHANGE, true, false);
    }

    @Bean
    public Queue codeQueue() {
        return new Queue(RabbitConstant.QUEUE, true);
    }

    @Bean
    public Binding codeBinding(DirectExchange codeExchange, Queue codeQueue) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(RabbitConstant.ROUTING_KEY);
    }
}
```

- [ ] **Step 4: 覆盖 application.yml**

`onlinejudge-submit-service/src/main/resources/application.yml`：

```yaml
server:
  port: 8103
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-submit-service
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
```

- [ ] **Step 5: 删除冒烟占位并编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
rm -f onlinejudge-submit-service/src/main/java/com/kun/onlinejudge/submitservice/controller/PingController.java \
      onlinejudge-submit-service/src/main/java/com/kun/onlinejudge/submitservice/controller/SmokeSendController.java \
      onlinejudge-submit-service/src/main/java/com/kun/onlinejudge/submitservice/config/RabbitSmokeConfig.java \
      onlinejudge-submit-service/src/main/java/com/kun/onlinejudge/submitservice/OnlinejudgeSubmitServiceApplication.java
mvn -pl onlinejudge-submit-service -am install -DskipTests -Dspring-boot.repackage.skip=true 2>&1 | tail -8
```
Expected: BUILD SUCCESS（无主类中间态用 repackage.skip；主类 Task S-1 重建）。common 因新增 RabbitConstant 也需编译：`mvn -pl onlinejudge-common -am install -DskipTests` 应绿。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "build(submit): prep deps/mq-constant/config; drop smoke placeholders"
```

---

### Task S-1: 迁移提交域（controller/service/mapper，判题 MQ，Feign 拼 user）

**Files:**
- Move(逐字，package `...submitservice`): 单体 `controller/QuestionSubmitController.java`、`service/QuestionSubmitService.java`、`service/impl/QuestionSubmitServiceImpl.java`、`mapper/QuestionSubmitMapper.java`
- Create: `.../submitservice/OnlinejudgeSubmitServiceApplication.java`、`.../submitservice/messagelist/RabbitMqProducer.java`（改造）
- Delete(不再需要): 单体残留 `messagelist/RabbitMqConfig`（在 submit 已由 config/RabbitConfig 提供）；`judgement` 引用一律不迁入（判题属 judge-service）。

**Interfaces:**
- Produces: `/api/question_submit/**` 全部端点；提交 → DB(WAITING) → MQ `code-queue`。Service 方法不再带 loginUser/request：
  - `void validQuestionSubmit(QuestionSubmit qs, boolean add)`
  - `QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest req)`
  - `QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit qs)`（内部用 UserContext 判 owner/admin 决定隐藏 code/judgeInfo）
  - `Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> page)`

**迁移与改造要点**
1. QuestionSubmitMapper：`...submitservice.mapper`，BaseMapper，无 XML。
2. QuestionSubmitService/Impl：删 `UserService`/`QuestionService` 注入；`validQuestionSubmit` 的"题目存在"改为 `QuestionServiceClient.getQuestionVOById(questionId)`，`data==null → BusinessException(NOT_FOUND,"题目不存在")`。user 拼装改 `UserServiceClient`（单条 getUserVOById null-safe；分页 listUserVOByIds 批量、判空/code!=0 抛 SYSTEM_ERROR）。`getQuestionSubmitVO/Page` 去掉 `User loginUser` 参数，owner/admin 判定内部改读 `UserContext.getUserId()/getUserRole()`（owner：userId.equals(uid)；admin：ADMIN_ROLE 等值）；`QuestionSubmitVO.setUser(userVO)`、owner/admin 之外清 code/judgeInfo（保原语义）。import 归位（model.result/model.request/exception/constant/annotation/utils.UserContext）。
3. QuestionSubmitController：删 `UserService` 与 `JudgeService` 注入及 import（judgeService 本就没用）；删 `RabbitMqProducer`? 保留（add 用）。loginUser 相关全改 `UserContext`：add 设 userId=`UserContext.getUserId()`（null→NOT_LOGIN）；delete/get 判 owner 用 `UserContext.getUserId()` 与 admin（`UserContext.getUserRole()`）；get/vo、list/page/vo 不再取 loginUser 传参（service 内部判断）；my/list/page/vo 取 uid 设查询 userId（uid null→NOT_LOGIN）。所有 HttpServletRequest 参数去掉。@AuthCheck(ADMIN)（update、list/page）保留。
4. `RabbitMqProducer`（`...submitservice/messagelist`）：与单体逻辑一致但交换机/路由键引用 `RabbitConstant`（`convertAndSend(RabbitConstant.EXCHANGE, RabbitConstant.ROUTING_KEY, message)`）。
5. 主类重建（scanBasePackages 自身+annotation/security/exception/config、@MapperScan(submitservice.mapper)、@EnableFeignClients(basePackages=serviceclient)）——参照 question-service 主类模板改包名。

**验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-submit-service -am install -DskipTests 2>&1 | tail -20
```
Expected: BUILD SUCCESS。

提交：`feat(submit): migrate question-submit domain with Feign + MQ producer`

---

### Task S-2: 启动与功能验收

**Files:** 无需新增（如验收暴露 bug 修复另提交）。

**验收前置**：MySQL/Redis/Nacos/Rabbit 就绪；question-service(8102) 与 user-service(8101) 运行（提交校验题目 + VO 拼 user 需要它们）。submit-service 8103。

- [ ] **Step 1: 编译并先后台启动 user/question/submit**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-submit-service -am install -DskipTests 2>&1 | tail -6
# 起 3 个服务(java -jar target/*.jar, 日志 /tmp, 记 PID, 轮询 8101/8102/8103)
```

- [ ] **Step 2: 验收用例（curl 记录原样输出；身份头模拟网关注入，如先经网关拿 token 亦可）**
1. 准备：register 一名普通用户（经 user-service 直连或网关）记 uid；以 admin（置 userRole=admin）建一题记 qid。
2. 提交：`POST :8103/api/question_submit/add`，body `{"questionId":$qid,"language":"java","code":"public class Main{...}"}`（参考 QuestionSubmitAddRequest 字段），带 `X-User-Id:$uid`、`X-User-Role:user`（普通用户即可，add 无 admin 要求）→ code 0 取 sid；随后 DB 中该行 status 应为 0(WAITING)；Rabbit 管理页 `code-queue` 出现该消息（积压≥1）——证明 MQ producer 发出。
3. 本人读 get/vo：`GET :8103/api/question_submit/get/vo?id=$sid` 带同 uid/user → code 0，含 code/judgeInfo（owner 可见）。
4. 他人读（可选）：换另一 uid/user 头访问 get/vo → code 0 但 code/judgeInfo 为空（非 owner 隐藏）。
5. my/list：`POST :8103/api/question_submit/my/list/page/vo` body `{"current":1,"pageSize":5}` 带 uid → code 0 且 records 该提交存在、`user.id==$uid`（Feign user 拼装）。
6. admin list/page：`POST :8103/api/question_submit/list/page` body `{"current":1,"pageSize":5}` 带 admin → code 0（@AuthCheck admin）；带 user → 40101。
7. 校验不存在的题目：add body questionId=999999 → code 40400（题目不存在，Feign→question-service 校验生效）。
8.（对照网关）经 `8888/api/question_submit/get/vo?id=$sid` 带真实登录 token → code 0。
完成后 kill 进程；`git status` clean。

- [ ] **Step 3: 提交（如有修复）**

```bash
git add -A
git commit -m "fix(submit): <摘要>"   # 无修复则跳过
```

---

## Self-Review 结论（作者已自查）

- 复用 question-service 已验证模式；submit 特有点为：判题经 MQ（常量收 common）、owner/admin 用 UserContext、validQuestionSubmit 校验题目走 Feign QuestionServiceClient。
- 判题消费端属 judge-service（下一步迁移），本段验收只验证"DB WAITING + MQ 消息发出"，不验证判题结果。
- Rabbit 拓扑在 submit 与(后续)judge 双侧声明（幂等），常量同源防漂移。
