# Auth Segment B2：网关统一鉴权（WebFlux）+ 端到端 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `onlinejudge-gateway`（Spring Cloud Gateway / WebFlux）成为统一鉴权入口：白名单放行 register/login/OPTIONS；其余请求验 `Authorization` access token + Redis 活跃会话 → 每请求经 load-balanced WebClient 调 user-service `/api/inner/user/{id}/snapshot` 取**最新用户** → 剥掉入站伪造 `X-User-*` 后注入真实身份头 → 转发下游；过期自动经 `X-Refresh-Token` 续签并把新 token 写回响应头；`ban` 用户网关硬拒。最后与 user-service 端到端验收"登录→携带身份头访问受保护端点、内网不可路由、伪造头被剥"。至此鉴权主干闭环，question/submit 等服务迁移可在其上直接进行。

**Architecture:** gateway 只依赖 `onlinejudge-model`（`model.result.*` 错误体、`model.auth.JwtUtils` 纯类）+ 自身 WebFlux 栈。会话校验用 `spring-boot-starter-data-redis-reactive` 的 `ReactiveStringRedisTemplate`（Redisson StringCodec 写入的纯字符串 key/value 可直读）。快照用 `@LoadBalanced WebClient.Builder`（Spring Cloud LoadBalancer 提供 `lb://` 解析）。错误体统一 `BaseResponse`、HTTP 200 + code（40100/40101/40102），保持前端契约。

**Tech Stack:** Java8、Spring Boot 2.6.13、Spring Cloud Gateway 2021.0.x(WebFlux)、spring-data-redis-reactive(Lettuce)、WebClient + spring-cloud-loadbalancer、jjwt(model 内)、Nacos。

## Global Constraints（本段必须遵守）

- 版本沿用聚合 pom；不改父 pom。**网关不得依赖 `onlinejudge-common`**（其含 servlet starter），只新增依赖 `onlinejudge-model` 与 `spring-boot-starter-data-redis-reactive`。
- 网关根包 `com.kun.onlinejudge.gateway`；只扫自身根包（`@SpringBootApplication` 默认即可，不整包扫 model/common——model 无 bean、无需扫）。
- 鉴权键/行为对齐：Redis key `session:<userId>:<deviceType>`=tokenId(7d)、`kicked:<userId>:<deviceType>:<tokenId>`；白名单与单体 JwtInterceptor allowlist 对齐（本段=OPTIONS + `POST /api/user/register` + `POST /api/user/login`）；续签响应头 `X-Access-Token`/`X-Refresh-Token`；错误 code 沿用 ErrorCode（NOT_LOGIN 40100 / NO_AUTH 40101 / KICKED_OFFLINE 40102，以 model.result.ErrorCode 实际值为准）。
- 快照语义：每请求调 user-service snapshot（响应 `BaseResponse<UserSnapshotVO>`）；`ban`→网关 40101 硬拒；服务仍以 @AuthCheck 作第二道。
- `/api/inner/**` 不设网关路由（现在只路由 `/api/user/**`、`/api/question/**`、`/api/submit/**`；inner 不在其中→不可经网关达）。
- E2E 验收在 MySQL/Redis/Nacos/Rabbit 就绪时进行（已确认在本机运行）。user-service 以 B1 产物运行。
- 提交信息风格 `feat(gateway)/refactor(gateway)`。

---

### Task B2-0: 网关依赖与配置就位

**Files:**
- Modify: `onlinejudge-gateway/pom.xml`（加 onlinejudge-model、spring-boot-starter-data-redis-reactive）
- Create: `onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/config/JwtConfig.java`
- Create: `onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/config/WebClientConfig.java`
- Overwrite: `onlinejudge-gateway/src/main/resources/application.yml`（追加 jwt/redis 段，保留既有路由）

**Interfaces:**
- Produces: 网关可装配 `model.auth.JwtUtils`(yml jwt.*)、`@LoadBalanced WebClient.Builder`；响应式 Redis 就位。

- [ ] **Step 1: gateway pom 增依赖**

在 `onlinejudge-gateway/pom.xml` 的 `<dependencies>` 内追加：

```xml
        <dependency>
            <groupId>com.kun</groupId>
            <artifactId>onlinejudge-model</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>
```

- [ ] **Step 2: 网关 JwtConfig**

`onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/config/JwtConfig.java`：

```java
package com.kun.onlinejudge.gateway.config;

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

- [ ] **Step 3: 网关 WebClient 配置（lb 负载均衡）**

`onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/config/WebClientConfig.java`：

```java
package com.kun.onlinejudge.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
```

- [ ] **Step 4: 网关 application.yml（保留既有路由与 nacos，追加 redis/jwt）**

`onlinejudge-gateway/src/main/resources/application.yml` 覆盖为：

```yaml
server:
  port: 8888
spring:
  application:
    name: onlinejudge-gateway
  redis:
    database: 2
    host: localhost
    port: 6379
    timeout: 5000
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
    gateway:
      discovery:
        locator:
          enabled: false
      routes:
        - id: user-route
          uri: lb://onlinejudge-user-service
          predicates:
            - Path=/api/user/**
        - id: question-route
          uri: lb://onlinejudge-question-service
          predicates:
            - Path=/api/question/**
        - id: submit-route
          uri: lb://onlinejudge-submit-service
          predicates:
            - Path=/api/submit/**
jwt:
  secret: onlinejudge-jwt-secret-key-2026-256bits
  access-token-expire: 900
  refresh-token-expire: 604800
```

- [ ] **Step 5: 编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-gateway -am install -DskipTests 2>&1 | tail -8
```
Expected: BUILD SUCCESS（网关现在依赖 model，无 servlet 冲突；若 WebClient/@LoadBalanced 解析失败则检查 loadbalancer 依赖）。

- [ ] **Step 6: 提交**

```bash
git add onlinejudge-gateway
git commit -m "feat(gateway): add model/redis-reactive deps + jwt/webclient config"
```

---

### Task B2-1: 统一鉴权 GlobalFilter

**Files:**
- Create: `onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/filter/AuthGlobalFilter.java`
- Create: `onlinejudge-gateway/src/main/java/com/kun/onlinejudge/gateway/filter/AuthResultJson.java`（小工具：写统一 JSON 错误体）

**Interfaces:**
- Produces: 全局鉴权过滤（Order=-100）。逻辑（与 Global Constraints 对齐）在实现里逐条落实。

- [ ] **Step 1: 编写 `AuthGlobalFilter`**

要点（完整代码含在实现中，行为必须满足下列语义，若个别 Spring API 与 Boot2.6 版本有出入以编译+运行实证校正并在报告说明）：
1. `implements GlobalFilter, Ordered`；`getOrder() = -100`。
2. 白名单（方法 OPTIONS 或 path ∈ {`/api/user/register`,`/api/user/login`}）→ 剥入站 `X-User-Id/X-User-Role/X-User-Name/X-Device-Type` 后放行（`chain.filter(exchange)`）。
3. 非白名单：取 `Authorization: Bearer ...`，缺失/非 Bearer → 写 `ResultUtils.error(NOT_LOGIN,"未登录")` 200 完成。
4. 解析 access：`jwtUtils.parseAccessToken`。成功→校验会话活跃：`ReactiveStringRedisTemplate.opsForValue().get(sessionKey)`，值==tokenId 才有效；session 缺失或值不等→检查 kicked 标记决定 40102/40100，完成。
   `ExpiredJwtException`→走续签：读 `X-Refresh-Token`，`parseRefreshToken`，校验会话（同上），从 refresh claims 取 userId/tokenId/deviceType/userRole/userName/userAvatar/unionId/mpOpenId 组装 `User`，生成新 tokenId+`generateAccessToken`/`generateRefreshToken`，更新 Redis session key，把新 token 写响应头 `X-Access-Token`/`X-Refresh-Token`，随后继续"取快照→注入头→放行"。无 refresh 或校验失败→40100 完成。
5. 认证通过后每请求取快照：用 `@LoadBalanced WebClient` GET `http://onlinejudge-user-service/api/inner/user/{id}/snapshot`，解 `BaseResponse<UserSnapshotVO>`（`data`）。若返回非 0 code 或连接失败→40100/500 错误完成。快照 userRole==ban→40101"账号已封禁"完成。
6. 剥除入站所有 `X-User-*` 头 → 注入 `X-User-Id`(snapshot.id)、`X-User-Role`(snapshot.userRole)、`X-User-Name`(snapshot.userName)、`X-User-Avatar`(snapshot.userAvatar) → `exchange.mutate().request(...)` 后 `chain.filter`。
7. 错误/成功均统一 JSON：content-type `application/json;charset=UTF-8`、HTTP 200、body = `BaseResponse` JSON（`ObjectMapper` 序列化）。
8. 无快照依赖的放行仅限白名单；其它一律走完整校验。`/api/inner/**` 无路由，本过滤器对它们也无特殊放行（进不来）。

`AuthResultJson.java`：提供 `static Mono<Void> write(ServerWebExchange ex, BaseResponse<?> body)`，内部设置 contentType + 写字节。

- [ ] **Step 2: 编译**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -pl onlinejudge-gateway -am install -DskipTests 2>&1 | tail -20
```
Expected: BUILD SUCCESS。若 `@LoadBalanced WebClient.Builder` bean 未生效（lb:// 无法解析），报告并按 Spring Cloud LoadBalancer 惯用法补 `LoadBalancedExchangeFilterFunction` 注入后复跑（记录实现差异）。

- [ ] **Step 3: 提交**

```bash
git add onlinejudge-gateway
git commit -m "feat(gateway): unified reactive auth filter (token+redis session+per-request fresh snapshot+strip/inject X-User-*)"
```

---

### Task B2-2: 端到端验收（网关+user-service）

**Files:**
- 无需改动（仅运行与回归）；若验收暴露问题则修复并另提交。

**Interfaces:**
- Consumes: user-service（B1，端口 8101）+ gateway（8888）+ Nacos + Redis + MySQL。

- [ ] **Step 1: 启动 user-service 与 gateway（后台，记录 PID）**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
java -jar onlinejudge-user-service/target/onlinejudge-user-service-0.0.1-SNAPSHOT.jar > /tmp/user.log 2>&1 &
java -jar onlinejudge-gateway/target/onlinejudge-gateway-0.0.1-SNAPSHOT.jar > /tmp/gw.log 2>&1 &
# 轮询就绪：8101 /api/user/register 可达、8888 可达；再确认 Nacos 里两个服务都在
```

- [ ] **Step 2: 验收用例（全部经网关 8888，curl 记录原样输出；除显式外不带手工 X 头）**

1. 放行：`POST 8888/api/user/register`（新随机账号）→ code 0（无需 token）。
2. 登录：`POST 8888/api/user/login`（同账号）→ code 0 取 accessToken/refreshToken/uid。
3. 受保护端点经网关、仅带 token：`GET 8888/api/user/get/login`（`Authorization: Bearer $AT`）→ code 0 且 data.userRole=user —— **证明网关已注入身份头**（user-service 不解析 token）。
4. 伪造头被剥：对第 3 步请求额外带 `X-User-Role: admin` → 仍 code 0 且 userRole=user（**网关剥掉外部伪造头，按其真实快照注入**）。若服务端返回 admin 则失败。
5. 无 token 访问受保护：`GET 8888/api/user/get/login`（无头）→ code 40100。
6. 内网不可路由：`GET 8888/api/inner/user/$uid/snapshot` → 非 200/路由失败（404/503）**说明 /inner 不经网关**；直连 `8101/api/inner/user/$uid/snapshot` 应 200（作对照）。
7. ban 硬拒（可选，若数据库可临时改）：把该用户 userRole 改 ban 后带其 token 请求受保护端点 → code 40101；验后改回。
8. 续签（可选，若想验证）：造一个马上过期的 access（可临时把 access-token-expire 调小重启验证）……若不便于改配置则记录"续签逻辑由代码评审+单测背书，未在 E2E 覆盖"。

- [ ] **Step 3: 结束清理**

kill 两个进程，确认 8101/8888 释放；`git status` clean。

- [ ] **Step 4: 提交（如有修复）**

```bash
git add -A
git commit -m "fix(gateway): <修复摘要>"
```
无修复则跳过。

---

## Self-Review 结论（作者已自查）

- **依赖边界**：网关只加 model（纯、无 servlet）与 reactive redis；不依赖 common/service-client——与"网关纯净"约束一致，WebFlux 不会因 spring-webmvc 冲突。
- **行为对齐**：白名单、错误码、会话键、续签头均沿用单体/既有约定；快照每请求拉取=用户"每请求回查最新 user/即时封禁"决策在网关落地；ban 硬拒比单体更严（设计 §修订）。
- **安全**：入站 X-User-* 先剥后注入；/inner 无路由；服务直连仍可伪造头——属已知信任边界，E2E 验收 6 覆盖网关层防护。
- **待实现时校正**：`@LoadBalanced WebClient.Builder` bean 是否在 gateway 下被 Spring Cloud LoadBalancer 处理（否则改用 `LoadBalancedExchangeFilterFunction`）；续签写入响应头时序；ErrorCode 数值以 `model.result.ErrorCode` 为准。这些以编译+E2E 实证校正并在报告中记录。
