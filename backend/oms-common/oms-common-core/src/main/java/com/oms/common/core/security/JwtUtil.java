package com.oms.common.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT 生成与解析工具（HS256）。
 */
public final class JwtUtil {

    private JwtUtil() {
    }

    public static String generateToken(String secret, JwtClaims claims, Duration ttl) {
        SecretKey key = key(secret);
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(claims.username())
                .claim("userId", claims.userId())
                .claim("userType", claims.userType())
                .claim("merchantId", claims.merchantId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，非法或过期抛出 {@link JwtException}。
     */
    public static JwtClaims parseToken(String secret, String token) {
        SecretKey key = key(secret);
        Claims payload = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtClaims(
                number(payload.get("userId")).longValue(),
                payload.getSubject(),
                number(payload.get("userType")).intValue(),
                payload.get("merchantId") == null ? null : number(payload.get("merchantId")).longValue());
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Number number(Object value) {
        return (Number) value;
    }
}
