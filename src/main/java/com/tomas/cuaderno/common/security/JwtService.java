package com.tomas.cuaderno.common.security;

import io.jsonwebtoken.*;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.tomas.cuaderno.auth.AuthProperties;

@Service
public class JwtService {
    private final PublicKey key;
    private final String issuer;
    private final String audience;
    public JwtService(AuthProperties properties) {
        key = readPublicKey(properties.getPublicKeyPem());
        issuer = require(properties.getIssuer(), "AUTH_JWT_ISSUER");
        audience = properties.getAudience() == null ? "" : properties.getAudience().trim();
    }
    public UUID subject(String token) {
        var parser = Jwts.parser().verifyWith(key).requireIssuer(issuer);
        if (!audience.isBlank()) parser.requireAudience(audience);
        var claims = parser.build().parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) throw new MalformedJwtException("Central JWT expiration is required");
        String subject = claims.getSubject();
        if (subject != null) {
            try { return UUID.fromString(subject); } catch (IllegalArgumentException ignored) { /* Try the central UUID claim below. */ }
        }
        // Older central tokens identify the username in sub and carry the UUID in uid.
        try { return UUID.fromString(claims.get("uid", String.class)); }
        catch (IllegalArgumentException | NullPointerException ignored) { throw new MalformedJwtException("Central JWT subject must be a UUID"); }
    }
    private String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value.trim();
    }
    private PublicKey readPublicKey(String pem) {
        if (pem == null || pem.isBlank()) throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is required");
        try {
            String value = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception ex) { throw new IllegalStateException("AUTH_PUBLIC_KEY_PEM is not a valid RSA public key", ex); }
    }
}
