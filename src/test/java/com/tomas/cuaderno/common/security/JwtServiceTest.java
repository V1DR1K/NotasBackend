package com.tomas.cuaderno.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;

class JwtServiceTest {
    @Test void createsTokenThatResolvesToPrincipalId() {
        SecurityProperties properties = new SecurityProperties(); properties.setJwtSecret("a-local-test-secret-with-at-least-32-bytes"); properties.setExpiration(Duration.ofMinutes(5));
        JwtService service = new JwtService(properties); UUID id = UUID.randomUUID();
        assertThat(service.subject(service.create(new AppPrincipal(id, "tomas", "hash", "ADMIN", true)))).isEqualTo(id);
    }
}
