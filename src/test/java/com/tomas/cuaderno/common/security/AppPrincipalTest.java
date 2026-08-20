package com.tomas.cuaderno.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppPrincipalTest {
    @Test void exposesRoleAsSpringAuthority() { assertThat(new AppPrincipal(UUID.randomUUID(), "tomas", "hash", "ADMIN", true).getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN"); }
}
