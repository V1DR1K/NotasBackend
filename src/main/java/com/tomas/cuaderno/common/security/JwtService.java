package com.tomas.cuaderno.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecurityProperties properties;
    private final Key key;
    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        if (properties.getJwtSecret() == null || properties.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }
    public String create(AppPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder().subject(principal.id().toString()).claim("username", principal.username()).claim("role", principal.role())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(properties.getExpiration()))).signWith(key).compact();
    }
    public UUID subject(String token) {
        return UUID.fromString(Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
