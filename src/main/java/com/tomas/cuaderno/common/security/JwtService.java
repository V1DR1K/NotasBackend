package com.tomas.cuaderno.common.security;

import io.jsonwebtoken.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.MessageDigest;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.tomas.cuaderno.auth.AuthProperties;
import com.tomas.cuaderno.auth.CentralAuthUser;
import com.tomas.cuaderno.auth.RsaKeyProvider;

@Service
public class JwtService {
    private final PublicKey key;
    private final RsaKeyProvider signingKeys;
    private final String issuer;
    private final String audience;
    private final boolean requireAudience;
    private final int accessTokenMinutes;
    @Autowired
    public JwtService(AuthProperties properties, RsaKeyProvider signingKeys) {
        this.signingKeys = signingKeys;
        key = readPublicKey(properties.getPublicKeyPem());
        issuer = require(properties.getIssuer(), "AUTH_JWT_ISSUER");
        requireAudience = properties.isRequireAudience();
        audience = requireAudience ? require(properties.getAudience(), "AUTH_JWT_AUDIENCE") : properties.getAudience();
        accessTokenMinutes = properties.getAccessTokenMinutes();
        if (accessTokenMinutes < 1 || accessTokenMinutes > 60) throw new IllegalStateException("AUTH_ACCESS_TOKEN_MINUTES must be between 1 and 60");
        if (!MessageDigest.isEqual(key.getEncoded(), signingKeys.publicKey().getEncoded())) {
            throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM does not match AUTH_PRIVATE_KEY_FILE");
        }
    }

    public JwtService(AuthProperties properties) {
        this.signingKeys = null;
        this.key = readPublicKey(properties.getPublicKeyPem());
        this.issuer = require(properties.getIssuer(), "AUTH_JWT_ISSUER");
        this.requireAudience = properties.isRequireAudience();
        this.audience = requireAudience ? require(properties.getAudience(), "AUTH_JWT_AUDIENCE") : properties.getAudience();
        this.accessTokenMinutes = properties.getAccessTokenMinutes();
    }

    public UUID subject(String token) {
        return identity(token).userId();
    }

    public TokenIdentity identity(String token) {
        var parser = Jwts.parser().verifyWith(key).requireIssuer(issuer);
        if (requireAudience) parser.requireAudience(audience);
        var claims = parser.build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null) throw new MalformedJwtException("Central JWT timestamps are required");
        UUID userId;
        try {
            String subject = claims.getSubject();
            userId = UUID.fromString(subject == null ? claims.get("uid", String.class) : subject);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            throw new MalformedJwtException("Central JWT subject must be a UUID");
        }
        return new TokenIdentity(userId, claims.get("username", String.class),
                claims.get("client_app", String.class), claims.get("role", String.class));
    }

    public String issueAccessToken(CentralAuthUser user, String appCode, String role) {
        if (signingKeys == null) throw new IllegalStateException("JWT signing keys are unavailable in validation-only mode");
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(signingKeys.keyId()).and()
                .subject(user.getId().toString())
                .claim("uid", user.getId().toString())
                .claim("username", user.getUsername())
                .claim("client_app", appCode)
                .claim("role", role)
                .issuer(issuer)
                .audience().add(require(audience, "AUTH_JWT_AUDIENCE")).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessTokenMinutes))))
                .id(UUID.randomUUID().toString())
                .signWith(signingKeys.privateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public long expiresInSeconds() { return Duration.ofMinutes(accessTokenMinutes).toSeconds(); }
    public record TokenIdentity(UUID userId, String username, String clientApp, String role) {}

    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }
    private PublicKey readPublicKey(String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is required");
        try {
            String value = pem.replace("\\n", "\n").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception ex) { throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is not a valid RSA public key", ex); }
    }
}
