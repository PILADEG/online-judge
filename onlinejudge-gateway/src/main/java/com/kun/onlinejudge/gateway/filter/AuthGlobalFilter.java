package com.kun.onlinejudge.gateway.filter;

import com.kun.onlinejudge.model.auth.JwtUtils;
import com.kun.onlinejudge.model.dto.user.RefreshTokenResult;
import com.kun.onlinejudge.model.entity.User;
import com.kun.onlinejudge.model.result.BaseResponse;
import com.kun.onlinejudge.model.result.ErrorCode;
import com.kun.onlinejudge.model.result.ResultUtils;
import com.kun.onlinejudge.model.vo.UserSnapshotVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权 GlobalFilter（WebFlux / Spring Cloud Gateway）。
 *
 * <p>Order = -100。行为语义与 Task B2-1 brief 对齐：
 * <ul>
 *   <li>白名单（OPTIONS 或 /api/user/register、/api/user/login）：剥掉入站伪造 X-User-* 与 X-Device-Type 后直接放行；</li>
 *   <li>非白名单：要求 {@code Authorization: Bearer <access>}，缺失/解析失败→ BaseResponse(NOT_LOGIN) HTTP 200；</li>
 *   <li>解析成功→ ReactiveStringRedisTemplate 校验活跃会话（session:{userId}:{deviceType} == tokenId）；
 *       session 缺失/不匹配→ 查 kicked:{userId}:{deviceType}:{tokenId} 决定 40102 / 40100；</li>
 *   <li>access 过期（ExpiredJwtException）→ 用 X-Refresh-Token 续签并轮换 Redis 会话，把新 token 写响应头
 *       X-Access-Token / X-Refresh-Token 后继续；无 refresh / 校验失败→ 40100；</li>
 *   <li>认证通过→ 每请求经 @LoadBalanced WebClient GET
 *       {@code http://onlinejudge-user-service/api/inner/user/{id}/snapshot} 取最新 user；role==ban→ 40101 硬拒；</li>
 *   <li>剥除入站 X-User-* 后注入 X-User-Id/Role/Name/Avatar（+ X-Device-Type 回填真实设备）再放行；</li>
 *   <li>错误体统一 HTTP 200 + BaseResponse JSON。</li>
 * </ul>
 *
 * <p>注：本模块不依赖 onlinejudge-common（其含 servlet starter），因此会话键前缀与身份头名在此以常量内联，
 * 与 common 的 RedisConstant/HeaderConstant 保持同构。错误码一律取自 model.result.ErrorCode，未硬编码数值。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);

    private static final int ORDER = -100;

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_REFRESH_TOKEN = "X-Refresh-Token";
    private static final String HEADER_NEW_ACCESS_TOKEN = "X-Access-Token";
    private static final String HEADER_NEW_REFRESH_TOKEN = "X-Refresh-Token";

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_AVATAR = "X-User-Avatar";
    private static final String HEADER_DEVICE_TYPE = "X-Device-Type";

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String KICKED_KEY_PREFIX = "kicked:";

    private static final String USER_SERVICE_BASE = "http://onlinejudge-user-service";
    private static final String USER_SNAPSHOT_PATH = "/api/inner/user/{id}/snapshot";

    private static final String BAN_ROLE = "ban";
    private static final String REFRESH_DURATION_DEFAULT = "604800";

    private static final List<String> WHITELIST_PATHS = new ArrayList<>();

    static {
        WHITELIST_PATHS.add("/api/user/register");
        WHITELIST_PATHS.add("/api/user/login");
    }

    private final JwtUtils jwtUtils;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final WebClient userWebClient;
    private final long refreshTokenExpireSeconds;

    public AuthGlobalFilter(JwtUtils jwtUtils,
                            ReactiveStringRedisTemplate redisTemplate,
                            WebClient.Builder webClientBuilder,
                            @Value("${jwt.refresh-token-expire:" + REFRESH_DURATION_DEFAULT + "}")
                            long refreshTokenExpireSeconds) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.userWebClient = webClientBuilder.build();
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        // 2. 白名单：OPTIONS 或 register/login，剥入站身份头后放行（无快照依赖）。
        if (isWhitelisted(request)) {
            ServerHttpRequest sanitized = stripIdentityHeaders(request);
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        // 3. 非白名单：必须携带 Bearer access token。
        String accessToken = resolveBearer(request.getHeaders().getFirst(HEADER_AUTHORIZATION));
        if (StringUtils.isBlank(accessToken)) {
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录"));
        }

        try {
            Claims claims = jwtUtils.parseAccessToken(accessToken);
            Long userId = jwtUtils.getUserId(claims);
            String tokenId = jwtUtils.getTokenId(claims);
            String deviceType = normalize(jwtUtils.getDeviceType(claims));
            if (userId == null || StringUtils.isBlank(tokenId)) {
                return AuthResultJson.write(exchange,
                        ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "登录态异常，请重新登录"));
            }
            String sessionKey = sessionKey(userId, deviceType);
            // 4. 校验活跃会话：session:{userId}:{deviceType} 的值 == tokenId 才有效。
            return redisTemplate.opsForValue().get(sessionKey)
                    .flatMap(activeTokenId -> {
                        if (tokenId.equals(activeTokenId)) {
                            return continueAuthorized(exchange, chain, userId, deviceType, null, null);
                        }
                        return rejectInactive(exchange, userId, deviceType, tokenId);
                    })
                    .switchIfEmpty(Mono.defer(() -> rejectInactive(exchange, userId, deviceType, tokenId)))
                    .onErrorResume(e -> onUnexpected(exchange, e, "校验会话状态失败"));
        } catch (ExpiredJwtException ex) {
            // access 过期 → 尝试用 X-Refresh-Token 续签并轮换会话。
            return tryRefresh(exchange, chain, ex);
        } catch (Exception e) {
            log.warn("gateway access token parse failed: {}", e.getMessage());
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录或登录态已失效"));
        }
    }

    // ------------------------------------------------------------------
    // 认证通过后的统一续程：取最新快照 → ban 硬拒 → 剥头/注入 → 放行。
    // ------------------------------------------------------------------

    private Mono<Void> continueAuthorized(ServerWebExchange exchange, GatewayFilterChain chain,
                                          Long userId, String deviceType,
                                          String newAccessToken, String newRefreshToken) {
        return fetchSnapshot(userId)
                .flatMap(snapshot -> {
                    if (snapshot == null || snapshot.getId() == null) {
                        // 快照缺失用户：会话指向的用户已不存在/数据异常 → 视为登录态失效。
                        return AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录"));
                    }
                    if (BAN_ROLE.equals(snapshot.getUserRole())) {
                        // 5. ban 用户网关硬拒。
                        return AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "账号已封禁"));
                    }
                    // 6. 剥除入站 X-User-* → 注入真实身份头 → 放行。
                    ServerHttpRequest authenticated = stripIdentityHeaders(exchange.getRequest()).mutate()
                            .headers(h -> injectIdentity(h, snapshot, deviceType))
                            .build();
                    // 续签成功：把新 token 写回响应头，客户端随后续请求续用。
                    if (StringUtils.isNotBlank(newAccessToken)) {
                        exchange.getResponse().getHeaders().set(HEADER_NEW_ACCESS_TOKEN, newAccessToken);
                        exchange.getResponse().getHeaders().set(HEADER_NEW_REFRESH_TOKEN, newRefreshToken);
                    }
                    return chain.filter(exchange.mutate().request(authenticated).build());
                })
                .onErrorResume(e -> onUnexpected(exchange, e, "拉取用户快照失败"));
    }

    private Mono<UserSnapshotVO> fetchSnapshot(Long userId) {
        return userWebClient.get()
                .uri(USER_SERVICE_BASE + USER_SNAPSHOT_PATH, userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<BaseResponse<UserSnapshotVO>>() {
                })
                .map(resp -> {
                    // 内网端点非 0 code（如用户已被删除）→ 视为该会话失效（调用方返回 40100）。
                    if (resp == null || resp.getCode() != 0) {
                        return null;
                    }
                    return resp.getData();
                });
    }

    // ------------------------------------------------------------------
    // 续签（refresh token 轮换）
    // ------------------------------------------------------------------

    private Mono<Void> tryRefresh(ServerWebExchange exchange, GatewayFilterChain chain, ExpiredJwtException expired) {
        String refreshToken = exchange.getRequest().getHeaders().getFirst(HEADER_REFRESH_TOKEN);
        if (StringUtils.isBlank(refreshToken)) {
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录"));
        }
        Claims refreshClaims;
        try {
            refreshClaims = jwtUtils.parseRefreshToken(refreshToken);
        } catch (Exception e) {
            log.warn("gateway refresh token parse failed: {}", e.getMessage());
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录"));
        }
        Long userId = jwtUtils.getUserId(refreshClaims);
        String oldTokenId = jwtUtils.getTokenId(refreshClaims);
        String deviceType = normalize(jwtUtils.getDeviceType(refreshClaims));
        if (userId == null || StringUtils.isBlank(oldTokenId)) {
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "登录态异常，请重新登录"));
        }
        String sessionKey = sessionKey(userId, deviceType);
        return redisTemplate.opsForValue().get(sessionKey)
                .flatMap(activeTokenId -> {
                    // refresh 对应的 tokenId 不再是活跃会话（已被轮换/踢下线）→ 交由 kicked 判定。
                    if (!oldTokenId.equals(activeTokenId)) {
                        return rejectInactive(exchange, userId, deviceType, oldTokenId);
                    }
                    // 会话活跃：生成新 tokenId 并轮换会话，写回新 token 后继续完整鉴权流程。
                    String newTokenId = UUID.randomUUID().toString();
                    User user = assembleUserForRefresh(userId, refreshClaims, expired);
                    String newAccess = jwtUtils.generateAccessToken(user, newTokenId, deviceType);
                    RefreshTokenResult newRefresh = jwtUtils.generateRefreshToken(user, deviceType, newTokenId);
                    return redisTemplate.opsForValue()
                            .set(sessionKey, newTokenId, Duration.ofSeconds(refreshTokenExpireSeconds))
                            .then(Mono.defer(() -> continueAuthorized(exchange, chain,
                                    userId, deviceType, newAccess, newRefresh.getToken())));
                })
                .switchIfEmpty(Mono.defer(() -> rejectInactive(exchange, userId, deviceType, oldTokenId)))
                .onErrorResume(e -> onUnexpected(exchange, e, "续签失败"));
    }

    /**
     * 组装续签所需 User：userRole/userName/userAvatar 仅写入过 access token，
     * 从已过期 access 的 claims（ExpiredJwtException.getClaims()）取回；
     * unionId/mpOpenId 从 refresh claims 取回（RefreshTokenResult 仅落这两者）。
     * 最终新生成的 access/refresh token 因此仍含完整身份信息。
     */
    private User assembleUserForRefresh(Long userId, Claims refreshClaims, ExpiredJwtException expired) {
        Claims expiredAccess = expired.getClaims();
        User user = new User();
        user.setId(userId);
        user.setUserRole(claimString(expiredAccess, "userRole"));
        user.setUserName(claimString(expiredAccess, "userName"));
        user.setUserAvatar(claimString(expiredAccess, "userAvatar"));
        user.setUnionId(claimString(refreshClaims, "unionId"));
        user.setMpOpenId(claimString(refreshClaims, "mpOpenId"));
        return user;
    }

    private String claimString(Claims claims, String key) {
        if (claims == null) {
            return null;
        }
        try {
            return claims.get(key, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 会话失效判定：40102（被踢） / 40100（未登录）
    // ------------------------------------------------------------------

    private Mono<Void> rejectInactive(ServerWebExchange exchange, Long userId, String deviceType, String tokenId) {
        if (StringUtils.isBlank(tokenId)) {
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录"));
        }
        String kickedKey = KICKED_KEY_PREFIX + userId + ":" + deviceType + ":" + tokenId;
        return redisTemplate.hasKey(kickedKey)
                .defaultIfEmpty(false)
                .flatMap(kicked -> kicked
                        ? AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.KICKED_OFFLINE, "账号已在其他设备登录"))
                        : AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录")))
                .onErrorResume(e -> onUnexpected(exchange, e, "检查踢下线标记失败"));
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private boolean isWhitelisted(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }
        return WHITELIST_PATHS.contains(request.getPath().value());
    }

    /** 从 Authorization 头解析 Bearer token；非 Bearer / 缺失返回 null。 */
    private String resolveBearer(String authorization) {
        if (StringUtils.isBlank(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = value.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.isBlank(token) ? null : token;
    }

    /** 剥除入站 X-User-* 与 X-Device-Type（防伪造），返回新请求。 */
    private static ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(AuthGlobalFilter::stripIdentityHeaderNames)
                .build();
    }

    /** 在（mutate 产生的）头副本上剔除身份头；不触碰原始请求头。 */
    private static void stripIdentityHeaderNames(HttpHeaders headers) {
        List<String> names = new ArrayList<>(headers.keySet());
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("x-user-") || "x-device-type".equals(lower)) {
                headers.remove(name);
            }
        }
    }

    private static void injectIdentity(HttpHeaders headers, UserSnapshotVO snapshot, String deviceType) {
        if (snapshot.getId() != null) {
            headers.set(HEADER_USER_ID, String.valueOf(snapshot.getId()));
        }
        if (StringUtils.isNotBlank(snapshot.getUserRole())) {
            headers.set(HEADER_USER_ROLE, snapshot.getUserRole());
        }
        if (StringUtils.isNotBlank(snapshot.getUserName())) {
            headers.set(HEADER_USER_NAME, snapshot.getUserName());
        }
        if (StringUtils.isNotBlank(snapshot.getUserAvatar())) {
            headers.set(HEADER_USER_AVATAR, snapshot.getUserAvatar());
        }
        if (StringUtils.isNotBlank(deviceType)) {
            headers.set(HEADER_DEVICE_TYPE, deviceType);
        }
    }

    private String sessionKey(Long userId, String deviceType) {
        return SESSION_KEY_PREFIX + userId + ":" + deviceType;
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    private Mono<Void> onUnexpected(ServerWebExchange exchange, Throwable e, String message) {
        log.error("gateway auth error [{}]: {}", message, e.getMessage());
        return AuthResultJson.write(exchange,
                ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试"));
    }
}
