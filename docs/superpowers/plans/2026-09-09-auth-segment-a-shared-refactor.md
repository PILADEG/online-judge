# Auth Segment A：共享件下沉 model + common 身份头/@AuthCheck 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成"鉴权主干"两段式的前半：(1) 把网关可复用的纯共享件（BaseResponse/ErrorCode/ResultUtils/JwtUtils）从 common 下沉到 **model**（无 servlet 依赖，WebFlux 网关可安全依赖 model，解 Foundation Forward 约束 #1）；(2) 在 common 落地 **身份头信任过滤器 + @AuthCheck 注解 + 角色切面**（服务侧只信网关注入的 `X-User-*` 头），并给出测试。user-service 登录签发与网关响应式鉴权留到 **Segment B**。

**Architecture:** `onlinejudge-model` 在纯数据之上成为"跨运行时（servlet 服务 + WebFlux 网关）共享"的最小家园：`model.result`(错误体) + `model.auth.JwtUtils`(纯工具)。`onlinejudge-common` 新增 `annotation.AuthCheck`、`security.IdentityHeaderFilter`、`security.AuthRoleAspect`、`constant` 头名常量。服务在迁移期显式扫描/导入 common 的 `annotation`/`security`/`exception` 包即可获得统一鉴权能力；网关不依赖 common，仅依赖 model 的 `model.result`/`model.auth`。

**Tech Stack:** Java8、Spring Boot 2.6.13、Spring Web(MockMvc for tests)、jjwt、Spring AOP。

## Global Constraints（与既有一致 + 本计划新增）

- 版本沿用聚合 pom 属性；本计划不改父 pom、不改任何版本值。
- `onlinejudge-model` **不得**依赖/import `com.kun.onlinejudge.common` 及任何 servlet/spring-web；可依赖 spring-beans（已有）、hutool、lombok、mybatis-plus-annotation，本计划新增 **jjwt**(api compile；impl/jackson runtime)。model 内新增类不得用 `@Component`/`@Value`/`javax.servlet`。
- `BaseResponse/ErrorCode/ResultUtils` 目标包：`com.kun.onlinejudge.model.result`（内容逐字搬运，仅改 package 行）。`JwtUtils` 目标包：`com.kun.onlinejudge.model.auth`，去掉 `@Component`/`@Value`，构造器改为显式 `JwtUtils(String secret, long accessTokenExpire, long refreshTokenExpire)`（供各运行时的 `@Bean` 配置注入，网关与 user-service 将各自按 yml `jwt.*` 构建）。
- 引用迁移点必须同步改 import（枚举见 Task A1 Step 3）：`onlinejudge-common/exception/{BusinessException,GlobalExceptionHandler,ThrowUtils}`、`onlinejudge-service-client/{UserServiceClient,QuestionServiceClient}`。
- common 新增鉴权件包路径：`com.kun.onlinejudge.annotation.AuthCheck`、`com.kun.onlinejudge.security.IdentityHeaderFilter`、`com.kun.onlinejudge.security.AuthRoleAspect`、`com.kun.onlinejudge.constant.HeaderConstant`；它们只依赖 common 既有件与 model（`model.result.ErrorCode`、`model.enums.UserRoleEnum`、`exception.BusinessException`、`utils.UserContext`）。
- 角色语义与单体一致（抄 `AuthInterceptor`）：未登录(`X-User-Role` 缺失)→`NOT_LOGIN_ERROR`；`mustRole` 为空→放行任意已登录角色；角色 `ban`→`NO_AUTH_ERROR`；`mustRole=admin`→仅 admin；`mustRole=user`→user 或 admin。
- 提交信息风格沿用 `feat(auth) / refactor(auth)`；每任务独立可验证+提交。

---

### Task A1: 共享错误体与 JWT 工具下沉到 model

**Files:**
- Create(从 common 逐字搬并改 package): `onlinejudge-model/src/main/java/com/kun/onlinejudge/model/result/BaseResponse.java`、`.../ErrorCode.java`、`.../ResultUtils.java`
- Create(纯类重写): `onlinejudge-model/src/main/java/com/kun/onlinejudge/model/auth/JwtUtils.java`
- Delete: `onlinejudge-common/src/main/java/com/kun/onlinejudge/common/{BaseResponse,ErrorCode,ResultUtils}.java`、`onlinejudge-common/src/main/java/com/kun/onlinejudge/utils/JwtUtils.java`
- Modify: `onlinejudge-model/pom.xml`(加 jjwt)、`onlinejudge-common/pom.xml`(test scope starter-test)、`onlinejudge-common/.../exception/*.java`(3 处 import)、`onlinejudge-service-client/.../{UserServiceClient,QuestionServiceClient}.java`(import)

**Interfaces:**
- Produces: `com.kun.onlinejudge.model.result.{BaseResponse,ErrorCode,ResultUtils}`；`com.kun.onlinejudge.model.auth.JwtUtils`（纯类，见下）。后续网关/user-service/服务迁移都引这组类型。

- [ ] **Step 1: 逐字搬 BaseResponse/ErrorCode/ResultUtils 并改 package 行**

```bash
SRC="D:/Java_project/onlineJudge-cloud/onlineJudge/onlinejudge-common/src/main/java/com/kun/onlinejudge/common"
DST="D:/Java_project/onlineJudge-cloud/onlineJudge/onlinejudge-model/src/main/java/com/kun/onlinejudge/model/result"
mkdir -p "$DST"
for f in BaseResponse.java ErrorCode.java ResultUtils.java; do
  sed 's/^package com\.kun\.onlinejudge\.common;/package com.kun.onlinejudge.model.result;/' "$SRC/$f" > "$DST/$f"
done
head -1 "$DST/BaseResponse.java" "$DST/ErrorCode.java" "$DST/ResultUtils.java"
```
Expected: 三文件首行均为 `package com.kun.onlinejudge.model.result;`。内部同包引用（如 ResultUtils→ErrorCode/BaseResponse）无需改 import；若原文件跨包 import 其它（如 `com.kun.onlinejudge.constant.*`），照常保留（model 不得引用 common/constant/utils/exception——见 Step 3 grep 兜底，命中则把常量等价内联后报告）。

- [ ] **Step 2: 新增纯类 `model/auth/JwtUtils.java`**

`onlinejudge-model/src/main/java/com/kun/onlinejudge/model/auth/JwtUtils.java` 内容：

```java
package com.kun.onlinejudge.model.auth;

import com.kun.onlinejudge.model.dto.user.RefreshTokenResult;
import com.kun.onlinejudge.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 纯工具（跨运行时共享：servlet 服务与 WebFlux 网关均可用）。
 * 无 spring 注解；由各运行时用 yml jwt.* 显式构造为 Bean。
 */
public class JwtUtils {

    private final SecretKey secretKey;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtUtils(String secret, long accessTokenExpire, long refreshTokenExpire) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    public String generateAccessToken(User user, String tokenId, String deviceType) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", user.getId())
                .claim("tokenId", tokenId)
                .claim("deviceType", deviceType)
                .claim("userRole", user.getUserRole())
                .claim("unionId", user.getUnionId())
                .claim("mpOpenId", user.getMpOpenId())
                .claim("userName", user.getUserName())
                .claim("userAvatar", user.getUserAvatar())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpire * 1000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public RefreshTokenResult generateRefreshToken(User user, String deviceType, String tokenId) {
        Date now = new Date();
        String token = Jwts.builder()
                .claim("userId", user.getId())
                .claim("tokenId", tokenId)
                .claim("deviceType", deviceType)
                .claim("unionId", user.getUnionId())
                .claim("mpOpenId", user.getMpOpenId())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenExpire * 1000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        return new RefreshTokenResult(token, tokenId);
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody();
    }

    public boolean isExpiredException(Exception e) {
        return e instanceof ExpiredJwtException;
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public String getTokenId(Claims claims) {
        return claims.get("tokenId", String.class);
    }

    public String getUserRole(Claims claims) {
        return claims.get("userRole", String.class);
    }

    public String getDeviceType(Claims claims) {
        return claims.get("deviceType", String.class);
    }
}
```

- [ ] **Step 3: 删除 common 侧旧文件并改引用 import**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
rm onlinejudge-common/src/main/java/com/kun/onlinejudge/common/BaseResponse.java \
   onlinejudge-common/src/main/java/com/kun/onlinejudge/common/ErrorCode.java \
   onlinejudge-common/src/main/java/com/kun/onlinejudge/common/ResultUtils.java \
   onlinejudge-common/src/main/java/com/kun/onlinejudge/utils/JwtUtils.java
```
再对下列文件把对 `com.kun.onlinejudge.common.{BaseResponse,ErrorCode,ResultUtils}` 的 import 行改为 `com.kun.onlinejudge.model.result.*`（按文件实际 import 出现处改，`sed -i` 或用编辑逐个）：
- `onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/BusinessException.java`
- `onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/GlobalExceptionHandler.java`
- `onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/ThrowUtils.java`
- `onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/UserServiceClient.java`
- `onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/QuestionServiceClient.java`

例（`sed` 批处理，注意把三个类名都替换）：
```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
for f in \
  onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/BusinessException.java \
  onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/GlobalExceptionHandler.java \
  onlinejudge-common/src/main/java/com/kun/onlinejudge/exception/ThrowUtils.java \
  onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/UserServiceClient.java \
  onlinejudge-service-client/src/main/java/com/kun/onlinejudge/serviceclient/QuestionServiceClient.java ; do
  sed -i 's/import com\.kun\.onlinejudge\.common\.\(BaseResponse\|ErrorCode\|ResultUtils\);/import com.kun.onlinejudge.model.result.\1;/' "$f"
done
grep -rn "import com.kun.onlinejudge.common.BaseResponse\|import com.kun.onlinejudge.common.ErrorCode\|import com.kun.onlinejudge.common.ResultUtils" --include=*.java . || echo "IMPORTS-CLEAN"
```
Expected: `IMPORTS-CLEAN`。

- [ ] **Step 4: 给 model 加 jjwt、给 common 加 test 依赖**

`onlinejudge-model/pom.xml` 在 `<dependencies>` 内（mybatis-plus-annotation 之后）追加：

```xml
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <scope>runtime</scope>
        </dependency>
```

`onlinejudge-common/pom.xml` 在 `<dependencies>` 内（lombok 之后）追加（Task A2 的测试用，A1 阶段一并就位即可）：

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 5: 构建 + 自包含校验**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
grep -rn "com.kun.onlinejudge.common\.\(BaseResponse\|ErrorCode\|ResultUtils\|JwtUtils\)\|com.kun.onlinejudge.utils.JwtUtils" --include=*.java onlinejudge-model || echo "MODEL-CLEAN"
mvn -pl onlinejudge-model -am install -DskipTests 2>&1 | tail -8
mvn -pl onlinejudge-common -am install -DskipTests 2>&1 | tail -8
mvn -DskipTests install 2>&1 | tail -8
```
Expected: `MODEL-CLEAN`；三段均 `BUILD SUCCESS`（common/service-client/可运行模块因 import 更新随之编译通过）。

- [ ] **Step 6: 提交**

```bash
git add onlinejudge-model onlinejudge-common onlinejudge-service-client
git commit -m "refactor(auth): sink BaseResponse/ErrorCode/ResultUtils & pure JwtUtils into onlinejudge-model"
```

---

### Task A2: common 身份头过滤器 + @AuthCheck 注解 + 角色切面（含测试）

**Files:**
- Create(common): `.../annotation/AuthCheck.java`、`.../constant/HeaderConstant.java`、`.../security/IdentityHeaderFilter.java`、`.../security/AuthRoleAspect.java`
- Test(common): `onlinejudge-common/src/test/java/com/kun/onlinejudge/security/AuthRoleAspectTest.java`

**Interfaces:**
- Consumes: `model.result.ErrorCode`、`model.enums.UserRoleEnum`、`common.exception.BusinessException`、`common.utils.UserContext`、`model.vo.LoginUserVO`（UserContext 内）。
- Produces: `@AuthCheck(mustRole)` 注解；`IdentityHeaderFilter`（`OncePerRequestFilter`，把 `X-User-Id/Role/Name/Device-Type` 写入请求属性，键与 UserContext 一致：`JWT_USER_ID/JWT_USER_ROLE/JWT_USER_NAME/JWT_DEVICE_TYPE`）；`AuthRoleAspect`（`@Around("@annotation(authCheck)")` 角色校验）；`HeaderConstant`。
- 语义（复用 `AuthRoleAspect`）：见 Global Constraints 角色语义；未登录抛 `BusinessException(NOT_LOGIN_ERROR)`，无权限抛 `NO_AUTH_ERROR`。

- [ ] **Step 1: 注解与常量**

`.../annotation/AuthCheck.java`（从单体逐字复制，包名不变）：
```java
package com.kun.onlinejudge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验（配合 common.security.AuthRoleAspect）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色（"" = 任意已登录角色）
     */
    String mustRole() default "";
}
```

`.../constant/HeaderConstant.java`：
```java
package com.kun.onlinejudge.constant;

/**
 * 网关注入的身份头名（服务侧据此信任当前登录用户）
 */
public interface HeaderConstant {

    String HEADER_USER_ID = "X-User-Id";
    String HEADER_USER_ROLE = "X-User-Role";
    String HEADER_USER_NAME = "X-User-Name";
    String HEADER_DEVICE_TYPE = "X-Device-Type";

    // 与 utils.UserContext 读取的请求属性键保持一致
    String ATTR_USER_ID = "JWT_USER_ID";
    String ATTR_USER_ROLE = "JWT_USER_ROLE";
    String ATTR_USER_NAME = "JWT_USER_NAME";
    String ATTR_DEVICE_TYPE = "JWT_DEVICE_TYPE";
}
```

- [ ] **Step 2: `security/IdentityHeaderFilter.java`**

```java
package com.kun.onlinejudge.security;

import com.kun.onlinejudge.constant.HeaderConstant;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 服务侧身份头过滤器：信任网关注入的 X-User-* 头，映射为请求属性，
 * 供 UserContext/@AuthCheck 读取。网关未注入（无头）时即为匿名请求。
 * 仅应部署于内网可达的服务入口；生产环境由网关统一鉴权。
 */
@Component
public class IdentityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        setLong(request, HeaderConstant.HEADER_USER_ID, HeaderConstant.ATTR_USER_ID);
        setString(request, HeaderConstant.HEADER_USER_ROLE, HeaderConstant.ATTR_USER_ROLE);
        setString(request, HeaderConstant.HEADER_USER_NAME, HeaderConstant.ATTR_USER_NAME);
        setString(request, HeaderConstant.HEADER_DEVICE_TYPE, HeaderConstant.ATTR_DEVICE_TYPE);
        filterChain.doFilter(request, response);
    }

    private void setLong(HttpServletRequest request, String header, String attr) {
        String v = request.getHeader(header);
        if (v != null) {
            try {
                request.setAttribute(attr, Long.valueOf(v));
            } catch (NumberFormatException ignored) {
                request.setAttribute(attr, null);
            }
        }
    }

    private void setString(HttpServletRequest request, String header, String attr) {
        String v = request.getHeader(header);
        if (v != null) {
            request.setAttribute(attr, v);
        }
    }
}
```

- [ ] **Step 3: `security/AuthRoleAspect.java`**

```java
package com.kun.onlinejudge.security;

import com.kun.onlinejudge.annotation.AuthCheck;
import com.kun.onlinejudge.exception.BusinessException;
import com.kun.onlinejudge.model.enums.UserRoleEnum;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 角色权限切面（语义与单体 AuthInterceptor 一致，但依赖身份头上下文而非 UserService）
 */
@Aspect
@Component
@Slf4j
public class AuthRoleAspect {

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        String userRole = UserContext.getUserRole();
        if (userRole == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        if (userRoleEnum == null || UserRoleEnum.BAN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.USER.equals(mustRoleEnum)
                && !UserRoleEnum.USER.equals(userRoleEnum)
                && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
```

- [ ] **Step 4: 测试（验证：无头=NOT_LOGIN；user 头访问 admin=NO_AUTH；admin 头访问 admin=放行）**

`onlinejudge-common/src/test/java/com/kun/onlinejudge/security/AuthRoleAspectTest.java`：

```java
package com.kun.onlinejudge.security;

import com.kun.onlinejudge.annotation.AuthCheck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = AuthRoleAspectTest.TestApp.class)
@AutoConfigureMockMvc
class AuthRoleAspectTest {

    @SpringBootApplication(scanBasePackages = "com.kun.onlinejudge")
    static class TestApp {
        public static void main(String[] args) {
            SpringApplication.run(TestApp.class, args);
        }
    }

    @RestController
    static class ProbeController {

        @AuthCheck(mustRole = "admin")
        @GetMapping("/probe/admin")
        public String admin() {
            return "admin-ok";
        }

        @AuthCheck
        @GetMapping("/probe/login")
        public String login() {
            return "login-ok";
        }

        @GetMapping("/probe/pub")
        public String pub() {
            return "pub-ok";
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void publicEndpoint_noHeader_ok() throws Exception {
        mvc.perform(get("/probe/pub"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void adminEndpoint_noHeader_notLogin() throws Exception {
        mvc.perform(get("/probe/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_LOGIN_ERROR.getCode()));
    }

    @Test
    void adminEndpoint_userRole_forbidden() throws Exception {
        mvc.perform(get("/probe/admin").header("X-User-Role", "user").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_AUTH_ERROR.getCode()));
    }

    @Test
    void adminEndpoint_adminRole_ok() throws Exception {
        mvc.perform(get("/probe/admin").header("X-User-Role", "admin").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("admin-ok"));
    }

    @Test
    void loginRequiredEndpoint_adminOrUser_ok() throws Exception {
        mvc.perform(get("/probe/login").header("X-User-Role", "user").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
```

> 说明：`scanBasePackages="com.kun.onlinejudge"` 会连带扫到兄弟模块的业务控制器吗？不会——common 模块的 classpath 只有 model/common 自身 + 测试类；module 内 `com.kun.onlinejudge` 下只有这些共享类与测试类，无业务 controller。返回体 `{code,data}` 由 common 的 `GlobalExceptionHandler`(exception 包)统一处理（`@RestControllerAdvice` 被扫入）。`BaseResponse.code` 用 `ErrorCode` 数值，0 表示成功（若单体 code 0 为成功，测试以实际 `ErrorCode` 常量核对，见 Step 5）。

- [ ] **Step 5: 核对 ErrorCode 数值语义并跑测试**

先读 `onlinejudge-model/.../model/result/ErrorCode.java` 确认 `SUCCESS`/`NOT_LOGIN_ERROR`/`NO_AUTH_ERROR` 的 `code` 取值与 `getCode()` 存在；若 `SUCCESS` 非 0，把 Step 4 测试中的 `code == 0` 改为 `ErrorCode.SUCCESS.getCode()`（否则测试会假红）。

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
grep -n "SUCCESS\|NOT_LOGIN_ERROR\|NO_AUTH_ERROR" onlinejudge-model/src/main/java/com/kun/onlinejudge/model/result/ErrorCode.java | head
mvn -pl onlinejudge-common test 2>&1 | tail -25
```
Expected: 该测试类 **5/5 PASS**（或按 ErrorCode 校正后全绿）。

- [ ] **Step 6: 全量构建 + 提交**

```bash
cd D:/Java_project/onlineJudge-cloud/onlineJudge
mvn -DskipTests install 2>&1 | tail -8
git add onlinejudge-common
git commit -m "feat(auth): add identity-header filter + @AuthCheck role aspect (trust X-User-* from gateway)"
```

---

## Self-Review 结论（作者已自查）

- **范围**：本 Segment A 不触碰 user-service/gateway 业务代码与冒烟占位；它们只在编译期因 import 变更被更新。user-service 登录签发、inner 快照端点、网关响应式鉴权属 Segment B。
- **一致性**：`model.result`/`model.auth` 无 servlet 依赖（BaseResponse/ErrorCode/ResultUtils 纯数据；JwtUtils 去掉 spring 注解）；common 新增 `security`/`annotation` 依赖 model 与 common 既有件，无循环。
- **行为对齐**：AuthRoleAspect 复刻单体 AuthInterceptor 判定顺序（未登录→mustRole 空放行→ban 拒绝→admin/user 分级）；@AuthCheck 用法与单体注解一致。
- **残留追踪**：`common` pom 的 jjwt/redisson 依赖在 Segment A 暂不清理（避免无谓 churn），待 Segment B/C 归属明确后统一收口（记入台账 minor）。
