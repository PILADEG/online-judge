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
import java.util.function.Function;
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
    private static final Duration SNAPSHOT_TIMEOUT = Duration.ofSeconds(3);
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
        if (isWhitelisted(request)) {
            ServerHttpRequest sanitized = stripIdentityHeaders(request);
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

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
            return redisTemplate.opsForValue().get(sessionKey)
                    .defaultIfEmpty("")
                    .flatMap(activeTokenId -> {
                        if (activeTokenId.isEmpty()) {
                            return rejectInactive(exchange, userId, deviceType, tokenId);
                        }
                        if (tokenId.equals(activeTokenId)) {
                            return continueAuthorized(exchange, chain, userId, deviceType);
                        }
                        return rejectInactive(exchange, userId, deviceType, tokenId);
                    })
                    .onErrorResume(e -> onUnexpected(exchange, e, "校验会话状态失败"));
        } catch (ExpiredJwtException ex) {
            return tryRefresh(exchange, chain, ex);
        } catch (Exception e) {
            log.warn("gateway access token parse failed: {}", e.getMessage());
            return AuthResultJson.write(exchange,
                    ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "未登录或登录态已失效"));
        }
    }

    private Mono<Void> continueAuthorized(ServerWebExchange exchange, GatewayFilterChain chain,
                                          Long userId, String deviceType) {
        return authorizedPipeline(exchange, chain, userId, deviceType,
                snapshot -> finishAuthorized(exchange, chain, deviceType, snapshot, null, null));
    }

    private Mono<Void> refreshAndAuthorize(ServerWebExchange exchange, GatewayFilterChain chain,
                                           Long userId, String deviceType, String sessionKey,
                                           Claims refreshClaims, ExpiredJwtException expired) {
        return authorizedPipeline(exchange, chain, userId, deviceType,
                snapshot -> rotateAndFinish(exchange, chain, userId, deviceType, sessionKey,
                        snapshot, refreshClaims, expired));
    }

    private Mono<Void> authorizedPipeline(ServerWebExchange exchange, GatewayFilterChain chain,
                                          Long userId, String deviceType,
                                          Function<UserSnapshotVO, Mono<Void>> onAuthorized) {
        return fetchSnapshot(userId)
                .flatMap(snapshot -> {
                    if (snapshot == null || snapshot.getId() == null) {
                        return AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, "登录状态已失效，请重新登录"));
                    }
                    if (!userId.equals(snapshot.getId())) {
                        log.error("gateway snapshot userId mismatch: expect={}, got={}", userId, snapshot.getId());
                        return AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试"));
                    }
                    if (BAN_ROLE.equals(snapshot.getUserRole())) {
                        return AuthResultJson.write(exchange,
                                ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "账号已封禁"));
                    }
                    return onAuthorized.apply(snapshot);
                })
                .onErrorResume(e -> onUnexpected(exchange, e, "拉取用户快照失败"));
    }

    private Mono<Void> finishAuthorized(ServerWebExchange exchange, GatewayFilterChain chain,
                                        String deviceType, UserSnapshotVO snapshot,
                                        String newAccessToken, String newRefreshToken) {
        ServerHttpRequest authenticated = stripIdentityHeaders(exchange.getRequest()).mutate()
                .headers(h -> injectIdentity(h, snapshot, deviceType))
                .build();
        if (StringUtils.isNotBlank(newAccessToken)) {
            exchange.getResponse().getHeaders().set(HEADER_NEW_ACCESS_TOKEN, newAccessToken);
            exchange.getResponse().getHeaders().set(HEADER_NEW_REFRESH_TOKEN, newRefreshToken);
        }
        return chain.filter(exchange.mutate().request(authenticated).build());
    }

    private Mono<Void> rotateAndFinish(ServerWebExchange exchange, GatewayFilterChain chain,
                                       Long userId, String deviceType, String sessionKey,
                                       UserSnapshotVO snapshot, Claims refreshClaims, ExpiredJwtException expired) {
        String newTokenId = UUID.randomUUID().toString();
        User user = assembleUserForRefresh(userId, refreshClaims, expired);
        String newAccess = jwtUtils.generateAccessToken(user, newTokenId, deviceType);
        RefreshTokenResult newRefresh = jwtUtils.generateRefreshToken(user, deviceType, newTokenId);
        return redisTemplate.opsForValue()
                .set(sessionKey, newTokenId, Duration.ofSeconds(refreshTokenExpireSeconds))
                .then(Mono.defer(() -> finishAuthorized(exchange, chain, deviceType,
                        snapshot, newAccess, newRefresh.getToken())));
    }

    private Mono<UserSnapshotVO> fetchSnapshot(Long userId) {
        return userWebClient.get()
                .uri(USER_SERVICE_BASE + USER_SNAPSHOT_PATH, userId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<BaseResponse<UserSnapshotVO>>() {
                })
                .map(resp -> {
                    if (resp == null || resp.getCode() != 0) {
                        return null;
                    }
                    return resp.getData();
                })
                .timeout(SNAPSHOT_TIMEOUT);
    }

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
                .defaultIfEmpty("")
                .flatMap(activeTokenId -> {
                    if (activeTokenId.isEmpty() || !oldTokenId.equals(activeTokenId)) {
                        return rejectInactive(exchange, userId, deviceType, oldTokenId);
                    }
                    return refreshAndAuthorize(exchange, chain, userId, deviceType, sessionKey,
                            refreshClaims, expired);
                })
                .onErrorResume(e -> onUnexpected(exchange, e, "续签失败"));
    }

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

    private boolean isWhitelisted(ServerHttpRequest request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return true;
        }
        return WHITELIST_PATHS.contains(request.getPath().value());
    }

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

    private static ServerHttpRequest stripIdentityHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(AuthGlobalFilter::stripIdentityHeaderNames)
                .build();
    }

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
