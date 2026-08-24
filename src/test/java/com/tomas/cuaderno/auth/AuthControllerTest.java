package com.tomas.cuaderno.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.security.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthControllerTest {
    @Test
    void logoutRevokesTheRefreshTokenWithoutUsingCookies() {
        CentralAuthClient central = mock(CentralAuthClient.class);
        AuthController controller = new AuthController(central, mock(LocalUserProvisioningService.class));

        controller.logout(new AuthDtos.RefreshRequest("refresh-token"));

        verify(central).logout("refresh-token");
    }

    @Test
    void loginReturnsBearerSessionAndProvisionsTheCentralUser() {
        CentralAuthClient central = mock(CentralAuthClient.class);
        LocalUserProvisioningService provisioning = mock(LocalUserProvisioningService.class);
        AuthController controller = new AuthController(central, provisioning);
        UUID centralId = UUID.randomUUID();
        User local = new User();
        local.setUsername("tomas");
        local.setRole("ADMIN");
        when(central.login("tomas", "password")).thenReturn(new CentralAuthClient.TokenResponse(
                "access-token", "refresh-token", "Bearer", 900,
                new CentralAuthClient.CentralUser(centralId, "tomas", false)));
        when(provisioning.provision(org.mockito.ArgumentMatchers.any())).thenReturn(local);

        AuthDtos.AuthResponse response = controller.login(new AuthDtos.LoginRequest("tomas", "password"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().username()).isEqualTo("tomas");
    }

    @Test
    void meUsesTheBearerTokenWhenCallingCentralAuth() {
        CentralAuthClient central = mock(CentralAuthClient.class);
        AuthController controller = new AuthController(central, mock(LocalUserProvisioningService.class));
        UUID id = UUID.randomUUID();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer access-token");
        when(central.me("access-token")).thenReturn(new CentralAuthClient.CentralUser(id, "tomas", true));

        AuthDtos.UserResponse response = controller.me(new AppPrincipal(id, "tomas", null, "ADMIN", true), request);

        assertThat(response.mustChangePassword()).isTrue();
        verify(central).me("access-token");
    }
}
