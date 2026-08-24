package com.lumencs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 双 Token：access（短效，默认 30min）+ refresh（长效，默认 7 天），
 * refresh 仅用于换取新 token 对，不直接用于接口鉴权。
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final int accessMinutes;
    private final int refreshHours;

    public JwtService(
            @Value("${lumencs.jwt.secret}") String secret,
            @Value("${lumencs.jwt.access-expire-minutes}") int accessMinutes,
            @Value("${lumencs.jwt.refresh-expire-hours}") int refreshHours) {
        String padded = secret.length() >= 32 ? secret : (secret + "0".repeat(32 - secret.length()));
        this.key = Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = accessMinutes;
        this.refreshHours = refreshHours;
    }

    public String issueAccess(String username) {
        return issue(username, TYPE_ACCESS, Duration.ofMinutes(accessMinutes));
    }

    public String issueRefresh(String username) {
        return issue(username, TYPE_REFRESH, Duration.ofHours(refreshHours));
    }

    private String issue(String username, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String parseUsername(String token) {
        return parse(token).getSubject();
    }

    /** 校验 token 类型（access / refresh），解析失败返回 false。 */
    public boolean isType(String token, String type) {
        try {
            return type.equals(parse(token).get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }
}
