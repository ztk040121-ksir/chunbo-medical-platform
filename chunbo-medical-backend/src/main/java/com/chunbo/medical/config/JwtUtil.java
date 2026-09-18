package com.chunbo.medical.config;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
@Component
public class JwtUtil {
    private static final String SECRET = "chunbo-medical-jwt-secret-key-2024-very-long";
    private static final long EXPIRATION_MS = 8 * 60 * 60 * 1000L;
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    public String generateToken(String username) {
        return generateToken(username, "USER");
    }
    public String generateToken(String username, String role) {
        return Jwts.builder().subject(username)
                .claim("role", role == null ? "USER" : role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(key).compact();
    }
    public String validateToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) { return null; }
    }
    /** 从 token 中解析角色，供鉴权过滤器做接口级权限校验 */
    public String extractRole(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Object role = claims.get("role");
            return role != null ? role.toString() : "USER";
        } catch (JwtException | IllegalArgumentException e) { return null; }
    }
}
