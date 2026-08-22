package com.tomas.cuaderno.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.tomas.cuaderno.auth.AuthProperties;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import java.util.Date;
import java.time.Instant;

class JwtServiceTest {
    @Test void resolvesCentralRs256Subject() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KeyPair pair = generator.generateKeyPair();
        AuthProperties properties = new AuthProperties();
        properties.setPublicKeyPem("-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        JwtService service = new JwtService(properties); UUID id = UUID.randomUUID();
        String token = Jwts.builder().subject(id.toString()).issuer(properties.getIssuer()).expiration(Date.from(Instant.now().plusSeconds(60))).signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
        assertThat(service.subject(token)).isEqualTo(id);
    }

    @Test void rejectsTokenWithoutExpiration() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); KeyPair pair = generator.generateKeyPair();
        AuthProperties properties = new AuthProperties(); properties.setPublicKeyPem(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
        JwtService service = new JwtService(properties); UUID id = UUID.randomUUID();
        String token = Jwts.builder().subject(id.toString()).issuer(properties.getIssuer()).signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.subject(token)).isInstanceOf(RuntimeException.class);
    }
}
