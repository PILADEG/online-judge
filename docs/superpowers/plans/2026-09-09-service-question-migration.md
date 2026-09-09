# Service 迁移：question-service（题目域）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把题目域迁成可独立运行的 `onlinejudge-question-service`：对外 `/api/question/**`（admin 增删改查/get，公开 get/vo、list/page、list/page/vo）+ 内网 `/api/inner/question/{id}`（供 submit 校验题目）。鉴权沿用网关统一鉴权语义：服务侧用 common 的 `IdentityHeaderFilter`+`AuthCheck`，admin 从 `X-User-*` 身份头判定；VO 拼"创建人 user"改为经 `UserServiceClient`(Feign) 调 user-service `/api/inner/user/**`（不再自连 user 表）。

**Architecture:** question-service 只持 `question` 表（单库直连）。`QuestionVO.user` 填充改 Feign → user-service。鉴权=身份头。作为 Feign 消费方启用 openfeign+loadbalancer+nacos。数据源/MyBatis 分页各自 config（同 user-service 模式），无 Redis/JWT（不做登录签发）。

**Tech Stack:** Java8、Spring Boot 2.6.13、MyBatis-Plus 3.5.2、MySQL、OpenFeign、Hutool。

## Global Constraints（本段必须遵守）

- 版本沿用聚合 pom；不改父 pom。只动 `onlinejudge-question-service`、必要时 model（不加）。
- question-service 根包 `com.kun.onlinejudge.questionservice`；删除 SB 冒烟占位（`PingController`、`SmokeUserPingClient`），重建 `OnlinejudgeQuestionServiceApplication`。
- 依赖方向：question-service 依赖 service-client(+common+model)；经 Feign 只调 user-service 内网端点；**不**依赖 submit/judge/gateway。
- 鉴权：`@AuthCheck(mustRole=ADMIN)` 读 `X-User-Role`；admin 创建题目取 `X-User-Id`（`UserContext.getUserId()`）。公开读端点不强制登录（网关仍要求已登录才放行——按网关白名单策略，本服务无需再区分）。
- VO user 填充：Feign `UserServiceClient.listUserVOByIds(ids)` 批量，服务端（user-service）脱敏；question-service 不再有 UserMapper/UserService。
- `/api/inner/**` 不做用户鉴权、不经网关。
- context-path=/api、端口 8102；数据源 `onlinejudge`（同 B1 配置）。
- `QuestionVO`/`Question`/DTO/枚举均在 model 就位；不新增 model 类。
- 提交信息风格 `feat(question)/refactor(question)`。

---

### Task Q-0: question-service 依赖与配置就位

**Files:**
- Modify: `onlinejudge-question-service/pom.xml`（追加 mybatis-plus-boot-starter、mysql-connector-java(runtime)）
- Create: `onlinejudge-question-service/src/main/java/com/kun/onlinejudge/questionservice/config/MyBatisPlusConfig.java`
- Overwrite: `onlinejudge-question-service/src/main/resources/application.yml`
- Delete: `onlinejudge-question-service/src/main/java/com/kun/onlinejudge/questionservice/controller/PingController.java`、`.../feign/SmokeUserPingClient.java`、`.../OnlinejudgeQuestionServiceApplication.java`（旧占位将被 Task Q-1 重建）

**Interfaces:**
- Produces: question-service 依赖集齐；本地 MP 分页配置；真实 yml（datasource/context-path/nacos）。`UserServiceClient`(service-client) 已在仓库（Task 5）。

- [ ] **Step 1: pom 追加依赖**

在 `onlinejudge-question-service/pom.xml` 的 `<dependencies>` 内追加：

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

- [ ] **Step 2: MyBatisPlusConfig**

`.../questionservice/config/MyBatisPlusConfig.java`：

```java
package com.kun.onlinejudge.questionservice.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **Step 3: 覆盖 `application.yml`（数据源同 B1；无 redis/jwt）**

`onlinejudge-question-service/src/main/resources/application.yml`：

```yaml
server:
  port: 8102
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-question-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/onlinejudge
    username: root
    password: 123456789
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

- [ ] **Step 4: 删除冒烟占位文件**

```bash
rm -f onlinejudge-question-service/src/main/java/com/kun/onlinejudge/questionservice/controller/PingController.java \
      onlinejudge-question-service/src/main/java/com/kun/onlinejudge/questionservice/feign/SmokeUserPingClient.java \
      onlinejudge-question-service/src/main/java/com/kun/onlinejudge/questionservice/OnlinejudgeQuestionServiceApplication.java
```

- [ ] **Step 5: 编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-question-service -am install -DskipTests 2>&1 | tail -8
```
Expected: BUILD SUCCESS（主类删除后该模块无 main，仅作为空壳编译；Task Q-1 重建）。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "build(question): prep deps/config; drop smoke placeholders"
```

---

### Task Q-1: 迁移题目域（controller/service/mapper，VO 拼 user 走 Feign）

**Files:**
- Move(逐字，package 改 `...questionservice`): 单体 `controller/QuestionController.java`、`service/QuestionService.java`、`service/impl/QuestionServiceImpl.java`、`mapper/QuestionMapper.java`
- Create: `.../questionservice/OnlinejudgeQuestionServiceApplication.java`（重建，见下）
- Modify: QuestionServiceImpl 注入 Feign 替换 UserService

**Interfaces:**
- Produces: `/api/question/**` 全部端点；question-service 依赖 Feign 调 user-service 填充 user。服务接口方法不再带 HttpServletRequest：
  - `void validQuestion(Question question, boolean add)`
  - `QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest request)`
  - `QuestionVO getQuestionVO(Question question)`
  - `Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage)`

**迁移与改造要点（实现时逐条落实；源文件在单体，改后复制到目标包）**
1. QuestionMapper：`package ...questionservice.mapper`，`extends BaseMapper<Question>`，无自定义 SQL。单体 `resources/mapper` 无 QuestionMapper.xml，不迁移。
2. QuestionService/Impl：删 `UserService userService` 注入与全部 `import com.kun.onlinejudge.service.UserService`；`getQuestionVO(Question)`/`getQuestionVOPage(Page)` 去掉 request 参数与 `HttpServletRequest` import。user 填充改为注入 `com.kun.onlinejudge.serviceclient.UserServiceClient`：
   - 单条：取 `question.getUserId()` → `userServiceClient.getUserVOById(userId).getData()` → set user（data 为空则置 null，不抛）。
   - 分页：收集 `userIdSet` → `userServiceClient.listUserVOByIds(new ArrayList<>(userIdSet))`；调用结果判 `resp==null||resp.getCode()!=0` → 抛 `BusinessException(ErrorCode.SYSTEM_ERROR,"获取用户信息失败")`；否则按 id 建 map，逐条 set user。
   - import 归位：`BaseResponse/ErrorCode/ResultUtils`→`model.result.*`；`PageRequest/DeleteRequest`→`model.request.*`；`BusinessException/ThrowUtils`→`exception.*`；`CommonConstant`→`constant.*`；VO/DTO/enum→model；`annotation.AuthCheck`→common；`utils.UserContext`→common。
3. QuestionController：`userService` 注入删除；admin 创建题目取当前用户 id 改为 `Long loginUserId = UserContext.getUserId(); if (loginUserId==null) throw BusinessException(NOT_LOGIN_ERROR);`；删除/更新/get/admin 列表的 `HttpServletRequest request` 参数去掉（方法体不再用）。公开端点 request 参数去掉。`@AuthCheck(mustRole=ADMIN)` 保留（UserConstant import → common.constant）。`model.result/model.request` import 归位。`JSONUtil`/BeanUtils 照旧。
4. 主类重建（含 Feign 启用与扫描）：

```java
package com.kun.onlinejudge.questionservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {
        "com.kun.onlinejudge.questionservice",
        "com.kun.onlinejudge.annotation",
        "com.kun.onlinejudge.security",
        "com.kun.onlinejudge.exception",
        "com.kun.onlinejudge.config"
})
@MapperScan("com.kun.onlinejudge.questionservice.mapper")
@EnableFeignClients(basePackages = "com.kun.onlinejudge.serviceclient")
public class OnlinejudgeQuestionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeQuestionServiceApplication.class, args);
    }
}
```

> Feign 只启用 `serviceclient` 包（不用冒烟 feign）。若与 common 扫描 bean 冲突，以精确 @Import 收敛并记录。

**验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-question-service -am install -DskipTests 2>&1 | tail -20
```
Expected: BUILD SUCCESS。

提交：`feat(question): migrate question domain (controller/service/mapper) with Feign user fill`

---

### Task Q-2: 内网端点 + 启动与功能验收

**Files:**
- Create: `.../questionservice/controller/QuestionInnerController.java`

**Interfaces:**
- Produces: `GET /api/inner/question/{id}` → `BaseResponse<QuestionVO>`（供 submit 校验/取标题；不含 answer/judgeCases）。

- [ ] **Step 1: QuestionInnerController**

```java
package com.kun.onlinejudge.questionservice.controller;

import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.entity.Question;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import com.kun.onlinejudge.model.vo.QuestionVO;
import com.kun.onlinejudge.questionservice.service.QuestionService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目服务内网端点：仅供其它服务内网调用，不做用户鉴权；不得经网关对外路由。
 */
@RestController
@RequestMapping("/inner/question")
public class QuestionInnerController {

    @Resource
    private QuestionService questionService;

    @GetMapping("/{id}")
    public BaseResponse<QuestionVO> getQuestionVOById(@PathVariable("id") Long id) {
        Question question = questionService.getById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(questionService.getQuestionVO(question));
    }
}
```

- [ ] **Step 2: 编译 + 启动 + 功能验收（需 user-service 8101 同时运行，MySQL/Nacos 就绪）**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-question-service -am install -DskipTests 2>&1 | tail -8
```
Expected: BUILD SUCCESS。然后先后台启动 **user-service** 与 **question-service**（`java -jar target/*.jar`，日志 /tmp，记 PID，轮询就绪）。验收（curl 记录原样输出）：
1. 准备管理员：经 user-service 直连（或经网关）register 一个账号 → 记 uid；若其 role 非 admin，用 SQL `UPDATE user SET userRole='admin' WHERE id=?` 置为 admin（本地 dev 库）。
2. admin 建题：`POST :8102/api/question/add`，带 `X-User-Id:$uid`、`X-User-Role:admin`（模拟网关注入身份头），body 为合法题目 JSON（含 title/content/tags/judgeCases/judgeConfig/answer 之一即可通过 validQuestion——需含 title/content/judgeCases/judgeConfig，参考 QuestionServiceImpl.validQuestion 必填）→ code 0 取新 qid。
3. 公开读：`GET :8102/api/question/get/vo?id=$qid`（无身份头）→ code 0，data 含 id/title，**不含 answer/judgeCases**；data.user 为 null（单条 getQuestionVO 不填 user？——若实现单条也填 user，则应含该 uid 的 UserVO；以实际为准记录）。
4. 分页 VO + user 填充：`POST :8102/api/question/list/page/vo` body `{"current":1,"pageSize":5}`（无身份头）→ code 0，且 page.records 中该题 `user` 存在且 `user.id==$uid`（**证明 Feign→user-service 内网填充生效**）。
5. inner：`GET :8102/api/inner/question/$qid`（无头）→ code 0、含 title、不含 answer；不存在的 id → 40400。
6. 越权：`POST :8102/api/question/delete` body `{"id":$qid}` 带 `X-User-Role:user` → code 40101；无头 → 40100；带 admin → code 0。
7. 对照网关（可选）：经网关 `8888/api/question/get/vo?id=$qid` 仅带真实登录 token（需在 user-service 登录拿 token）→ code 0（网关注入身份头）。
完成后 kill 两进程；`git status` clean。

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat(question): add /inner/question VO endpoint; verify question-service E2E"
```

---

## Self-Review 结论（作者已自查）

- **范围**：只动 question-service + 重建主类；model/service-client 已就位（UserServiceClient/QuestionVO 在仓库），无需改。
- **鉴权一致性**：service 不解析 token；admin 由 `X-User-Role` 经 `AuthRoleAspect` 判定；创建人 id 取 `X-User-Id`。与 B1/B2 已闭环的网关语义一致。
- **跨服务**：question VO user 填充唯一跨服务依赖，走 `UserServiceClient.listUserVOByIds`（批量，非 1+N）；QuestionVO 公开不含 answer/judgeCases（脱敏在内网端点同样成立）。
- **清理**：SB 冒烟占位（PingController/SmokeUserPingClient）删除，网关 question 路由现指向真实 question-service。
