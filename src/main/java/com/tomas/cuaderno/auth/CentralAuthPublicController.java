package com.tomas.cuaderno.auth;

import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CentralAuthPublicController {
    private final RsaKeyProvider keys;

    public CentralAuthPublicController(RsaKeyProvider keys) { this.keys = keys; }

    @GetMapping("/api/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        Map<String, String> jwk = Map.of(
                "kty", "RSA", "use", "sig", "alg", "RS256", "kid", keys.keyId(),
                "n", encode(keys.modulus()), "e", encode(keys.exponent()));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .body(Map.of("keys", java.util.List.of(jwk)));
    }

    private String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
