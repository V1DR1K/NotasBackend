package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final CentralAuthClient central;
    private final LocalUserProvisioningService provisioning;

    public AuthController(CentralAuthClient central, LocalUserProvisioningService provisioning) {
        this.central = central; this.provisioning = provisioning;
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        CentralAuthClient.TokenResponse tokens = central.login(request.username(), request.password());
        return session(tokens);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return session(central.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody(required = false) AuthDtos.RefreshRequest request) {
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

    private AuthDtos.AuthResponse session(CentralAuthClient.TokenResponse tokens) {
        if (tokens == null || tokens.user() == null || tokens.accessToken() == null || tokens.refreshToken() == null) throw new IllegalStateException("Central auth returned an incomplete session");
        User local = provisioning.provision(tokens.user());
        return new AuthDtos.AuthResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn(),
                new AuthDtos.UserResponse(local.getId(), tokens.user().username(), local.getRole(), tokens.user().mustChangePassword()));
    }

    private String accessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new BadCredentialsException("Bearer token is missing");
        }
        return authorization.substring(7);
    }
}
