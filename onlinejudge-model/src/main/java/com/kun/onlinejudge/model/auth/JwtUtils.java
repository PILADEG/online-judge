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
