package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final CentralAuthClient central;
    private final LocalUserProvisioningService provisioning;
    private final AuthProperties properties;

    public AuthController(CentralAuthClient central, LocalUserProvisioningService provisioning, AuthProperties properties) {
        this.central = central; this.provisioning = provisioning; this.properties = properties;
    }

    public AuthController(CentralAuthClient central, LocalUserProvisioningService provisioning) {
        this(central, provisioning, new AuthProperties());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        CentralAuthClient.TokenResponse tokens = central.login(request.username(), request.password());
        writeCookies(response, tokens);
        return ResponseEntity.ok(session(tokens, false));
    }

    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        return session(central.login(request.username(), request.password()), true);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        CentralAuthClient.TokenResponse tokens = central.refresh(requiredCookie(request, AuthCookie.REFRESH_TOKEN));
        writeCookies(response, tokens);
        return ResponseEntity.ok(session(tokens, false));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        central.logout(AuthCookie.read(request, AuthCookie.REFRESH_TOKEN));
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    public void logout(AuthDtos.RefreshRequest request) {
        central.logout(request == null ? null : request.refreshToken());
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AppPrincipal principal, HttpServletRequest request) {
        CentralAuthClient.CentralUser centralUser = central.me(accessToken(request));
        return new AuthDtos.UserResponse(principal.id(), centralUser.username(), principal.role(), centralUser.mustChangePassword());
    }

    @PutMapping("/change-password")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request, HttpServletRequest httpRequest) {
        central.changePassword(accessToken(httpRequest), request.currentPassword(), request.newPassword());
        return new AuthDtos.MessageResponse("Password changed");
    }

    private AuthDtos.AuthResponse session(CentralAuthClient.TokenResponse tokens, boolean exposeTokens) {
        if (tokens == null || tokens.user() == null || tokens.accessToken() == null || tokens.refreshToken() == null) throw new IllegalStateException("Central auth returned an incomplete session");
        User local = provisioning.provision(tokens.user());
        AuthDtos.UserResponse user = new AuthDtos.UserResponse(local.getId(), tokens.user().username(), local.getRole(), tokens.user().mustChangePassword());
        return exposeTokens ? new AuthDtos.AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn(), user) : new AuthDtos.AuthResponse(user);
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

    private void writeCookies(HttpServletResponse response, CentralAuthClient.TokenResponse tokens) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.ACCESS_TOKEN, tokens.accessToken(), Duration.ofSeconds(tokens.expiresIn())).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.REFRESH_TOKEN, tokens.refreshToken(), Duration.ofDays(properties.getRefreshCookieDays())).toString());
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.ACCESS_TOKEN, "", Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(AuthCookie.REFRESH_TOKEN, "", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(true).sameSite("Strict").path("/api").maxAge(maxAge).build();
    }
}
