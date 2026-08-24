package com.tomas.cuaderno.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tomas.cuaderno.common.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthControllerTest {
    @Test
    void logoutClearsEverySessionCookieWhenRefreshCookieIsInvalid() {
        CentralAuthClient central = mock(CentralAuthClient.class);
        AuthProperties authProperties = new AuthProperties();
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setSecureCookie(true);
        AuthController controller = new AuthController(central, mock(LocalUserProvisioningService.class), authProperties, securityProperties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(authProperties.getRefreshCookieName(), "revoked"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.logout(request, response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(3);
        assertThat(cookies).allMatch(cookie -> cookie.contains("Max-Age=0"));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("CUADERNO_AUTH="));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("CUADERNO_REFRESH="));
        assertThat(cookies).anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=") && !cookie.contains("HttpOnly"));
        verify(central).logout("revoked");
    }
}
