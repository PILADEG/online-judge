package com.kun.onlinejudge.utils;
import io.jsonwebtoken.Claims;
import com.kun.onlinejudge.model.dto.user.RefreshTokenResult;
import com.kun.onlinejudge.model.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {
    private final SecretKey secretKey;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtUtils(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token-expire}") long accessTokenExpire,
                   @Value("${jwt.refresh-token-expire}") long refreshTokenExpire) {
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
