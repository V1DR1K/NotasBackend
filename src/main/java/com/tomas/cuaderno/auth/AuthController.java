package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.security.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final CentralAuthClient central;
    private final LocalUserProvisioningService provisioning;
    private final AuthProperties authProperties;
    private final SecurityProperties securityProperties;

    public AuthController(CentralAuthClient central, LocalUserProvisioningService provisioning, AuthProperties authProperties, SecurityProperties securityProperties) {
        this.central = central; this.provisioning = provisioning; this.authProperties = authProperties; this.securityProperties = securityProperties;
    }

    @PostMapping("/login")
    public AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        CentralAuthClient.TokenResponse tokens = central.login(request.username(), request.password());
        return setSession(tokens, response);
    }

    @PostMapping("/refresh")
    public AuthDtos.UserResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        CentralAuthClient.TokenResponse tokens = central.refresh(cookie(request, authProperties.getRefreshCookieName()));
        return setSession(tokens, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        central.logout(cookie(request, authProperties.getRefreshCookieName()));
        clearCookie(response, securityProperties.getCookieName());
        clearCookie(response, authProperties.getRefreshCookieName());
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        CentralAuthClient.CentralUser centralUser = central.me(cookie(request, securityProperties.getCookieName()));
        return new AuthDtos.UserResponse(principal.id(), centralUser.username(), principal.role(), centralUser.mustChangePassword());
    }

    @PostMapping("/change-password")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request, HttpServletRequest httpRequest) {
        central.changePassword(cookie(httpRequest, securityProperties.getCookieName()), request.currentPassword(), request.newPassword());
        return new AuthDtos.MessageResponse("Password changed");
    }

    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) { return new AuthDtos.CsrfResponse(token.getToken()); }

    private AuthDtos.UserResponse setSession(CentralAuthClient.TokenResponse tokens, HttpServletResponse response) {
        if (tokens == null || tokens.user() == null || tokens.accessToken() == null || tokens.refreshToken() == null) throw new IllegalStateException("Central auth returned an incomplete session");
        User local = provisioning.provision(tokens.user());
        long accessAge = tokens.expiresIn() > 0 ? tokens.expiresIn() : 900;
        addCookie(response, securityProperties.getCookieName(), tokens.accessToken(), Duration.ofSeconds(accessAge));
        addCookie(response, authProperties.getRefreshCookieName(), tokens.refreshToken(), authProperties.getRefreshCookieMaxAge());
        return new AuthDtos.UserResponse(local.getId(), tokens.user().username(), local.getRole(), tokens.user().mustChangePassword());
    }

    private void addCookie(HttpServletResponse response, String name, String value, Duration maxAge) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name, value).httpOnly(true).secure(securityProperties.isSecureCookie()).sameSite("Lax").path("/").maxAge(maxAge).build().toString());
    }
    private void clearCookie(HttpServletResponse response, String name) { addCookie(response, name, "", Duration.ZERO); }
    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
}
