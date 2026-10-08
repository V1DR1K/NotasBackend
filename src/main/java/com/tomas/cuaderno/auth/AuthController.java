package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.auth.CentralAuthDtos.TokenResponse;
import com.tomas.cuaderno.common.security.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final CentralAuthService central;
    private final CentralAuthClient legacyCentral;
    private final LocalUserProvisioningService provisioning;
    private final AuthProperties properties;

    @Autowired
    public AuthController(CentralAuthService central, LocalUserProvisioningService provisioning, AuthProperties properties) {
        this.central = central;
        this.legacyCentral = null;
        this.provisioning = provisioning;
        this.properties = properties;
    }

    public AuthController(CentralAuthClient legacyCentral, LocalUserProvisioningService provisioning, AuthProperties properties) {
        this.central = null;
        this.legacyCentral = legacyCentral;
        this.provisioning = provisioning;
        this.properties = properties;
    }

    public AuthController(CentralAuthClient legacyCentral, LocalUserProvisioningService provisioning) {
        this(legacyCentral, provisioning, new AuthProperties());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        TokenResponse tokens = central.login(request.username(), request.password(), "notes");
        writeCookies(response, tokens);
        return ResponseEntity.ok(session(tokens, false));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        if (legacyCentral == null) return session(central.login(request.username(), request.password(), "notes"), true);
        CentralAuthClient.TokenResponse old = legacyCentral.login(request.username(), request.password());
        CentralAuthDtos.TokenResponse tokens = new CentralAuthDtos.TokenResponse(old.accessToken(), old.refreshToken(),
                old.tokenType(), old.expiresIn(), new CentralAuthDtos.CentralUser(old.user().id(), old.user().username(),
                        "ACTIVE", null, null, old.user().mustChangePassword(), null));
        return session(tokens, true);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        TokenResponse tokens = central.refresh(requiredCookie(request, AuthCookie.REFRESH_TOKEN), "notes");
        writeCookies(response, tokens);
        return ResponseEntity.ok(session(tokens, false));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        central.logout(AuthCookie.read(request, AuthCookie.REFRESH_TOKEN), "notes");
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    public void logout(AuthDtos.RefreshRequest request) {
        if (legacyCentral != null) legacyCentral.logout(request == null ? null : request.refreshToken());
        else central.logout(request == null ? null : request.refreshToken(), "notes");
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        if (legacyCentral != null) {
            CentralAuthClient.CentralUser identity = legacyCentral.me(accessToken(request));
            return new AuthDtos.UserResponse(principal.id(), identity.username(), principal.role(), identity.mustChangePassword());
        }
        var identity = central.me(accessToken(request), "notes");
        return new AuthDtos.UserResponse(principal.id(), identity.username(), identity.role(), identity.mustChangePassword());
    }

    @PutMapping("/change-password")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest body, HttpServletRequest request) {
        if (legacyCentral != null) legacyCentral.changePassword(accessToken(request), body.currentPassword(), body.newPassword());
        else central.changePassword(accessToken(request), body.currentPassword(), body.newPassword(), "notes");
        return new AuthDtos.MessageResponse("Password changed");
    }

    private AuthDtos.AuthResponse session(TokenResponse tokens, boolean exposeTokens) {
        if (tokens == null || tokens.user() == null || tokens.accessToken() == null || tokens.refreshToken() == null) {
            throw new IllegalStateException("Central auth created an incomplete session");
        }
        User local = provisioning.provision(tokens.user());
        AuthDtos.UserResponse user = new AuthDtos.UserResponse(local.getId(), tokens.user().username(), tokens.user().role(), tokens.user().mustChangePassword());
        return exposeTokens
                ? new AuthDtos.AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn(), user)
                : new AuthDtos.AuthResponse(user);
    }

    private String accessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7) return authorization.substring(7);
        String cookie = AuthCookie.read(request, AuthCookie.ACCESS_TOKEN);
        if (cookie != null && !cookie.isBlank()) return cookie;
        throw new BadCredentialsException("Authentication token is missing");
    }

    private String requiredCookie(HttpServletRequest request, String name) {
        String value = AuthCookie.read(request, name);
        if (value == null || value.isBlank()) throw new BadCredentialsException("Authentication cookie is missing");
        return value;
    }

    private void writeCookies(HttpServletResponse response, TokenResponse tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.ACCESS_TOKEN, tokens.accessToken(), Duration.ofSeconds(tokens.expiresIn())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofDays(properties.getRefreshCookieDays())).toString());
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.ACCESS_TOKEN, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.REFRESH_TOKEN, "", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(properties.isSecureCookie()).sameSite("Strict").path("/api").maxAge(maxAge).build();
    }
}
