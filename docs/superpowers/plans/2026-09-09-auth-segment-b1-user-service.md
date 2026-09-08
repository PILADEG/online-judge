# Auth Segment B1：user-service 登录/会话/内网快照迁移 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把用户域业务迁成可独立运行的 `onlinejudge-user-service`：账号注册/密码登录（发 access/refresh token + Redis 会话）、登出、`/user/get/login`、基于身份头的受保护端点（admin 增删改查、个人信息）、并暴露两个**内网端点**：`GET /api/inner/user/{id}/snapshot`（网关每请求取最新用户，承接"每请求回查"语义）与 `GET/POST /api/inner/user/{id}/vo`、`/list/vo`（供 question/submit 拼 VO）。鉴权一律走 common 的 `IdentityHeaderFilter`+`@AuthCheck`（信任 `X-User-*`），微信登录相关代码移除。

**Architecture:** user-service 是第一个真实业务服务：依赖 common(model/result、exception、annotation、security、config.MyBatisPlus/Redis 等共用件) + service-client(Feign 出参契约，本段暂只用 model/result/内部类型)。本地配置起真实 MySQL(`onlinejudge`)、Redis、Rabbit 无关。登录态 = JWT(access/refresh) + Redisson 会话簿记，与单体一致；`JwtUtils` 用 model.auth 纯类，由本服务 `@Bean` 按 yml `jwt.*` 构造。身份头由网关注入（本段用"直连 + 手置 `X-User-*` 头"做功能验证，生产仅网关）。

**Tech Stack:** Java8、Spring Boot 2.6.13、MyBatis-Plus 3.5.2、MySQL、Redisson、jjwt、Hutool。

## Global Constraints（本段必须遵守）

- 版本沿用聚合 pom；不改父 pom；不改 model/common/service-client/gateway/judge/question/submit 的既有内容（除非本计划明列）。
- user-service 根包 `com.kun.onlinejudge.userservice`；**删除** SB 冒烟占位（`OnlinejudgeUserServiceApplication` 保命替换为新主类同路径、`PingController` 删除）。
- 依赖方向：user-service 依赖 service-client(+common+model)；**不得**反向依赖 question/submit/judge。
- 鉴权原语用 common：`annotation.AuthCheck`、`security.IdentityHeaderFilter`、`security.AuthRoleAspect`、`utils.UserContext`、`exception.BusinessException`、`constant.UserConstant`。`@SpringBootApplication` 需扫描/导入 common 的这些包（推荐 `scanBasePackages={"com.kun.onlinejudge.userservice","com.kun.onlinejudge.annotation","com.kun.onlinejudge.security","com.kun.onlinejudge.exception","com.kun.onlinejudge.config"}`，config 只导入本服务需要的 `MyBatisPlusConfig`/`RedissonConfig`/`CorsConfig`；禁整包 `com.kun.onlinejudge`，以免把 `JwtUtils`@Bean 之类重复注册——本段把数据源/MyBatisPlus/Redisson/JwtUtils 的 `@Configuration` 放 user-service 自身包内，见 Task B1-1）。
- wx：删除 `userLoginByMpOpen`(service/接口) 与一切 `me.chanjar`/`WxOpenConfig` 引用；`/user/login/wx_open` 不存在（单体本就 stub，无前端依赖）。
- `/api/inner/**` 端点**不做用户鉴权**（内网互信），但不得从网关路由出去（B2 处理）；本段在 user-service 内 `IdentityHeaderFilter` 对 `/inner/**` 无需身份头也可直连返回。
- 密码与安全：SALT="kun"、MD5 加盐、逻辑删除 isDelete、`map-underscore-to-camel-case=false` 与单体一致；数据库为既有 `onlinejudge` 库。
- `server.servlet.context-path=/api`；端口 8101。

---

### Task B1-0: 清死依赖 + 准备 pom

**Files:**
- Modify: `onlinejudge-common/pom.xml`(移除死 jjwt)、`onlinejudge-user-service/pom.xml`(加 mybatis-plus-boot-starter、mysql-connector-j、redisson、可选 knife4j 延后；去掉无用的 SB 冒烟仅留基线)、`onlinejudge-user-service/src/main/resources/application.yml`(覆盖真实配置)
- Delete: `onlinejudge-user-service/src/main/java/com/kun/onlinejudge/userservice/controller/PingController.java`（冒烟占位）

**Interfaces:**
- Produces: user-service 依赖集齐（数据源/MP/redis/redisson/jjwt 均由聚合 pom 托管）；common 移除死 jjwt（终审 M1）。

- [ ] **Step 1: common 移除已死的 jjwt 依赖**

在 `onlinejudge-common/pom.xml` 删除 `jjwt-api`/`jjwt-impl`/`jjwt-jackson` 三个 dependency（JwtUtils 已下沉 model；common 源码零使用）。保留 redisson 与其余。

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
grep -n "jjwt" onlinejudge-common/pom.xml
```
Expected: 无输出（删除后）。

- [ ] **Step 2: user-service pom 增补依赖**

在 `onlinejudge-user-service/pom.xml` 的 `<dependencies>` 内（`spring-cloud-starter-loadbalancer` 之后）追加：

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
        <dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
        </dependency>
```

- [ ] **Step 3: 删除 user-service 冒烟 PingController**

```bash
rm onlinejudge-user-service/src/main/java/com/kun/onlinejudge/userservice/controller/PingController.java
```

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "build(user): prep user-service deps; drop dead jjwt from common; remove smoke ping"
```

---

### Task B1-1: user-service 基础设施配置（数据源/MP/Redisson/JwtUtils）

**Files:**
- Create: `onlinejudge-user-service/src/main/java/com/kun/onlinejudge/userservice/config/MyBatisPlusConfig.java`
- Create: `onlinejudge-user-service/src/main/java/com/kun/onlinejudge/userservice/config/RedissonConfig.java`
- Create: `onlinejudge-user-service/src/main/java/com/kun/onlinejudge/userservice/config/JwtConfig.java`
- Overwrite: `onlinejudge-user-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: 本服务独占的 `@Configuration`（不依赖 common 内未启用的 config，避免与 Future 网关争 bean）：MyBatis 分页插件、Redisson `scriptRedissonClient`(StringCodec)/`redissonClient`、`JwtUtils` Bean(yml jwt.*)。

- [ ] **Step 1: MyBatisPlusConfig（分页 + 逻辑删除；不放 @MapperScan——主类负责 @MapperScan）**

`.../userservice/config/MyBatisPlusConfig.java`：

```java
package com.kun.onlinejudge.userservice.config;

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

- [ ] **Step 2: RedissonConfig（bean 名与单体一致：`scriptRedissonClient` + `redissonClient`，绑定 `spring.redis`）**

`.../userservice/config/RedissonConfig.java`：

```java
package com.kun.onlinejudge.userservice.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "spring.redis")
@Data
public class RedissonConfig {

    private String host;
    private int port;
    private int database;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port).setDatabase(database);
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient scriptRedissonClient() {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.useSingleServer().setAddress("redis://" + host + ":" + port).setDatabase(database);
        return Redisson.create(config);
    }
}
```

- [ ] **Step 3: JwtConfig（yml jwt.* → JwtUtils Bean）**

`.../userservice/config/JwtConfig.java`：

```java
package com.kun.onlinejudge.userservice.config;

import com.kun.onlinejudge.model.auth.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtUtils jwtUtils(@Value("${jwt.secret}") String secret,
                             @Value("${jwt.access-token-expire}") long access,
                             @Value("${jwt.refresh-token-expire}") long refresh) {
        return new JwtUtils(secret, access, refresh);
    }
}
```

- [ ] **Step 4: 覆盖 `application.yml`（真实数据源/Redis/jwt；context-path=/api）**

`onlinejudge-user-service/src/main/resources/application.yml`：

```yaml
server:
  port: 8101
  servlet:
    context-path: /api
spring:
  application:
    name: onlinejudge-user-service
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/onlinejudge
    username: root
    password: 123456789
  redis:
    database: 2
    host: localhost
    port: 6379
    timeout: 5000
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
jwt:
  secret: onlinejudge-jwt-secret-key-2026-256bits
  access-token-expire: 900
  refresh-token-expire: 604800
```

> 说明：数据源/redis 账号按本机单体 dev 实配（localhost/root/123456789、db2），若你本机不同，落地时以实际为准并在此文件同步。

- [ ] **Step 5: 验证主类可起（先起空壳确认配置不炸）**

`OnlinejudgeUserServiceApplication` 保持现位于 `com.kun.onlinejudge.userservice`；本任务暂不改其扫描。运行：

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-user-service -am install -DskipTests 2>&1 | tail -8
```
Expected: BUILD SUCCESS（此处仅编译；启动验证放到 B1-3 迁移完业务后）。

- [ ] **Step 6: 提交**

```bash
git add onlinejudge-common onlinejudge-user-service
git commit -m "feat(user): add datasource/mybatis-plus/redisson/jwt config to user-service"
```

---

### Task B1-2: 迁移用户域业务（mapper/service/controller，去除 wx，改身份头鉴权）

**Files:**
- Move(逐字, package 改到 userservice 根下): 
  - 单体 `mapper/UserMapper.java` → `onlinejudge-user-service/.../com/kun/onlinejudge/userservice/mapper/UserMapper.java`（package `...userservice.mapper`）
  - 单体 `service/UserService.java` + `service/impl/UserServiceImpl.java` → `.../service/`、`.../service/impl/`（package `...userservice.service`、`...userservice.service.impl`），并按下述 delta 修改
  - 单体 `controller/UserController.java` → `.../controller/UserController.java`（package `...userservice.controller`），并按下述 delta 修改
- Create(本服务): `.../controller/UserInnerController.java`（snapshot + vo 内网端点）
- 单体 `resources/mapper/UserMapper.xml`：内容为空壳 resultMap，本模块**不迁移**（无自定义 SQL）
- 主类改为带 `@MapperScan` 与 `scanBasePackages`

**Interfaces:**
- Produces: 对外 `/api/user/**`（register/login/logout/get/login/get/vo/list/page(vo)/add/delete/update/update/my）+ `/api/inner/user/**`。controller 不再 import `me.chanjar`、不再用 `HttpSession`/`HttpServletRequest` 拉登录；身份一律 `UserContext`。

**Delta 修改（逐条）**
1. `UserService.java`/`UserServiceImpl.java`：删除 `userLoginByMpOpen` 方法声明与实现及其 imports（WxOAuth2UserInfo/me.chanjar/HttpServletRequest 相关/HttpSession/USER_LOGIN_STATE）。`userLogin` 保持。`getLoginUser`/`getLoginUserPermitNull`/`isAdmin(HttpServletRequest)`/`userLogout(HttpServletRequest)` 全部去掉 request 参数，改为内部 `UserContext.getUserId()`（null→NOT_LOGIN / null 允许场景返回 null）；`isAdmin()` 直接用 `UserContext.getUserRole()` 判 admin。删除 session 回退分支。`userLogout(String refreshToken)` 保留。
2. `UserController.java`：删除 `WxOpenConfig`、`me.chanjar`、`HttpServletRequest`、`HttpSession`、`UserContext`(保留 utils.UserContext 供 getLogin) 等不再用的 imports 与方法参数；`userLogin` 的 `HttpServletRequest request` 参数去掉（device UA 仍从前端传？——单体 login 用 `request.getHeader("User-Agent")`。改为 controller 用 `@RequestHeader(value="User-Agent", required=false)` 取 UA 传 `userLogin(account,pwd,userAgent)`）。`getLoginUser` 直接 `ResultUtils.success(UserContext.getLoginUser())`（为 null 抛 NOT_LOGIN）。admin 端点 `@AuthCheck(mustRole=UserConstant.ADMIN_ROLE)` 保留（包路径改 `com.kun.onlinejudge.annotation.AuthCheck`，UserConstant 在 common）。`DeleteRequest`/`PageRequest` import 改 `com.kun.onlinejudge.model.request.*`；`BaseResponse`/`ResultUtils`/`ErrorCode` import 改 `com.kun.onlinejudge.model.result.*`。
3. `UserServiceImpl` 中 `scriptRedissonClient`/`redissonClient` 由本服务 RedissonConfig 提供（bean 名不变）；`jwtUtils` 类型改为 `com.kun.onlinejudge.model.auth.JwtUtils`（@Resource byType 即可）。`SALT` 保持 static。所有 `com.kun.onlinejudge.common.*`(BaseResponse/ResultUtils/ErrorCode) import → `model.result.*`；`com.kun.onlinejudge.utils.*`(DeviceUtils/SqlUtils/UserContext) → common 的 `com.kun.onlinejudge.utils.*`（common 仍在原包，import 不变）；`com.kun.onlinejudge.exception.*`、`com.kun.onlinejudge.constant.*` 同 common（原包不变）。
4. `UserInnerController`：见 B1-3 任务内联新增（snapshot/vo 两个内网端点）。也可在本任务一并创建。

**验证**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-user-service -am install -DskipTests 2>&1 | tail -20
```
Expected: BUILD SUCCESS（若单体迁移件仍引用 common 旧的 `BaseResponse` 等——本段 task 已把 model 侧就位，改 import 后应编过；若报 UserMapper/User.xml 找不到或其它，按上述包/import 规则修正后复跑）。改完主类（见 B1-3）后再提交；本任务若主类未改无法编译则一并改主类再提交。

提交：`feat(user): migrate user domain (mapper/service/controller) off wx & session onto identity headers`（含主类扫描调整）

---

### Task B1-3: 主类装配 + 内网端点 + 启动与功能验收

**Files:**
- Modify: `.../userservice/OnlinejudgeUserServiceApplication.java`
- Create: `.../userservice/controller/UserInnerController.java`
- Create: `onlinejudge-model/src/main/java/com/kun/onlinejudge/model/vo/UserSnapshotVO.java`

**Interfaces:**
- Produces: 可独立启动的 user-service；`/api/inner/user/{id}/snapshot`、`/api/inner/user/{id}/vo`、`/api/inner/user/list/vo`；主类装配扫描 common 鉴权件。

- [ ] **Step 1: 主类扫描与 Mapper 装配**

`OnlinejudgeUserServiceApplication.java` 覆盖为：

```java
package com.kun.onlinejudge.userservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.kun.onlinejudge.userservice",
        "com.kun.onlinejudge.annotation",
        "com.kun.onlinejudge.security",
        "com.kun.onlinejudge.exception",
        "com.kun.onlinejudge.config"
})
@MapperScan("com.kun.onlinejudge.userservice.mapper")
public class OnlinejudgeUserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnlinejudgeUserServiceApplication.class, args);
    }
}
```

> 说明：`scanBasePackages` 里的 `com.kun.onlinejudge.config` 会把 common 的 `CorsConfig/JsonConfig` 引入（需要）；common 内已**无** BaseResponse/JwtUtils/MyBatisPlus/RedissonConfig，故不会与本服务自身 config 冲突。若启动出现 bean 冲突，改以精确 `@Import` 引入 CorsConfig/JsonConfig/exception handler，并去掉对应扫描包——记录到报告。

- [ ] **Step 2: `model.vo.UserSnapshotVO`（网关每请求快照 DTO）**

`onlinejudge-model/src/main/java/com/kun/onlinejudge/model/vo/UserSnapshotVO.java`：

```java
package com.kun.onlinejudge.model.vo;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户认证快照（供网关注入身份头前的最后一次权威校验）
 */
@Data
public class UserSnapshotVO implements Serializable {

    private Long id;
    private String userRole;   // user/admin/ban
    private String userName;
    private String userAvatar;

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 3: 内网端点 `UserInnerController`**

`.../userservice/controller/UserInnerController.java`：

```java
package com.kun.onlinejudge.userservice.controller;

import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.entity.User;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import com.kun.onlinejudge.model.vo.UserSnapshotVO;
import com.kun.onlinejudge.model.vo.UserVO;
import com.kun.onlinejudge.userservice.service.UserService;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户服务内部端点：仅供网关/其它服务内网调用，不做用户鉴权；不得经网关对外路由。
 */
@RestController
@RequestMapping("/inner/user")
public class UserInnerController {

    @Resource
    private UserService userService;

    /** 网关每请求取最新认证快照 */
    @GetMapping("/{id}/snapshot")
    public BaseResponse<UserSnapshotVO> snapshot(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        UserSnapshotVO vo = new UserSnapshotVO();
        vo.setId(user.getId());
        vo.setUserRole(user.getUserRole());
        vo.setUserName(user.getUserName());
        vo.setUserAvatar(user.getUserAvatar());
        return ResultUtils.success(vo);
    }

    @GetMapping("/{id}/vo")
    public BaseResponse<UserVO> getUserVOById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        return ResultUtils.success(userService.getUserVO(user));
    }

    @PostMapping("/list/vo")
    public BaseResponse<List<UserVO>> listUserVOByIds(@RequestBody List<Long> ids) {
        return ResultUtils.success(userService.getUserVO(userService.listByIds(ids)));
    }
}
```

- [ ] **Step 4: 编译 + 启动 + 功能验收（直连本服务，身份头手动注入模拟网关）**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-user-service -am install -DskipTests 2>&1 | tail -15
```
Expected: BUILD SUCCESS。然后**后台**启动（记录 PID）：
```bash
java -jar onlinejudge-user-service/target/onlinejudge-user-service-0.0.1-SNAPSHOT.jar > /tmp/user-svc.log 2>&1 &
echo "PID=$!"
# 等待就绪
for i in $(seq 1 30); do curl -s -o /dev/null http://127.0.0.1:8101/api/user/register && break; sleep 1; done
```
功能验收（全部 `curl -s` + 断言关键字段）：
1. 注册新账号（不重复随机名）：`POST /api/user/register` body `{"userAccount":"u_$(date +%s)","userPassword":"12345678x","checkPassword":"12345678x"}` → data 为新 id。
2. 登录：`POST /api/user/login` body 同上 → data 含 `accessToken`/`refreshToken`/`loginUserVO`。取 token 存变量。
3. `GET /api/user/get/login` 带 `Authorization: Bearer $TOKEN` + `X-User-Role`/`X-User-Id`? —— 验证：直连本服务时身份头由网关注入，故测试需**带身份头**（模拟网关）访问：`curl -H "Authorization: Bearer $TOKEN" -H "X-User-Id: $UID" -H "X-User-Role: admin" .../api/user/get/login` → data.userRole 正确。
   > 若你认为"登录后 /get/login 凭 token 即可（不含身份头）"——注意单体语义是"token→JWT interceptor→UserContext"；本架构改为"网关→身份头→服务"。**本服务在网关存在前不解析 token**，因此直连 /get/login 必须带身份头。这是预期行为（token 由网关解析）。
4. inner：`GET /api/inner/user/$UID/snapshot`（无身份头也应 200）→ data.userRole。
5. inner 批量：`POST /api/inner/user/list/vo` body `[$UID]` → 数组。
6. admin 保护：`POST /api/user/add` 带 `X-User-Role: admin` → 200；带 `X-User-Role: user` → code 40101(NO_AUTH)；无头 → 40100。
7. 登出：`POST /api/user/logout`（带身份头）→ 200（随后旧 token 会话被删；如要验 401 需网关）。

把每条命令与输出关键行写进报告，标注 PASS/FAIL；结束后 kill 进程。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(user): wire user-service app, add /inner/user snapshot+vo, UserSnapshotVO in model"
```

---

## Self-Review 结论（作者已自查）

- **范围**：B1 只动 user-service + 一个 model VO；不改 question/submit/judge/gateway。question/submit/judge 的 SB 冒烟占位保留到各自迁移（FC3），但 question 冒烟 Feign 指向的 user `/api/inner/ping` 被删——question 冒烟运行时才会暴露（本段不运行 question）。
- **鉴权语义**：本段 user-service 在"网关未上线"前提下用身份头模拟网关；token 解析放网关（B2）。`/get/login` 直连需身份头是预期。内网端点无鉴权、不对外路由（B2 约束）。
- **数据一致**：数据源=单体 dev(onlinejudge/root/123456789, db2)；SALT/逻辑删除/camelCase 关 均与单体一致。
- **残留追踪**：common config 扫描面（CorsConfig/JsonConfig）在 B2 网关上线后复核；Admin 端点在网关上线前仅靠身份头模拟测试。

---

## Epilogue — B1 实测要点 & B2 前置（2026-09-09）

B1 收口：全量 `mvn -DskipTests install` EXIT=0、工作树 clean；四项任务逐审查通过（含运行时验收 PASS）。

B2（网关响应式鉴权）开工前已确认/需注意：
1. **内网快照契约已就位**：`GET /api/inner/user/{id}/snapshot`（UserSnapshotVO: id/userRole/userName/userAvatar；用户不存在 40400）。网关每请求据此注入身份头。新注册用户 userRole 依赖 DB 默认（实测为 user，OK）。
2. **user-service 登录态端点需身份头**：网关前 user-service 不解析 token；`GET /api/user/get/login`、admin 端点、`/user/logout` 都读 `X-User-*`。B2 网关负责：验 token+Redis 会话 → 拉 snapshot → 注入 `X-User-Id/Role/Name(+Avatar)`。
3. **40100/40101 语义已实测**：服务侧 @AuthCheck（common）按身份头角色判；网关未过/角色不符时由服务抛码即可（B2 网关侧可预先拒 ban/40100，服务作为第二道）。
4. **Redis 会话键**：`session:<userId>:<deviceType>` 存 tokenId（7d）、`kicked:...`；值由 Redisson StringCodec 写入（纯字符串），网关用 spring-data-redis-reactive 的 StringRedisTemplate 可直接读。
5. **回归面**：删除 user `/inner/ping` 后，question 冒烟 Feign（SmokeUserPingClient → /api/inner/ping）仅在运行 question 时会暴露；question/submit/judge 冒烟占位保留到各自迁移（FC3）。`GET /inner/user/{id}/vo` 对不存在 id 会 50000（可后修）。B1 新发现的 minor：无 X-User-Avatar 头、新账号 userName/userAvatar 空、/get/vo 匿名可查 VO——均记录待统一。
