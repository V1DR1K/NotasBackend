package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.auth.CentralAuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CentralAuthApiController {
    private final CentralAuthService auth;
    private final AuthProperties properties;

    public CentralAuthApiController(CentralAuthService auth, AuthProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest body) {
        String clientApp = body.clientApp() == null || body.clientApp().isBlank() ? "whatplan" : body.clientApp();
        RegistrationResponse result = auth.register(body.username(), body.password(), clientApp);
        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody Credentials body) {
        return noStore(auth.login(body.username(), body.password(), body.clientApp()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest body) {
        return noStore(auth.refresh(body.refreshToken(), body.clientApp()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest body) {
        auth.logout(body.refreshToken(), body.clientApp());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestParam(defaultValue = "notes") String clientApp) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.me(bearer(authorization), clientApp));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                @Valid @RequestBody ChangePasswordRequest body) {
        auth.changePassword(bearer(authorization), body.currentPassword(), body.newPassword(), body.clientApp());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/health")
    public ResponseEntity<MessageResponse> health() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new MessageResponse("Central authentication is available"));
    }

    private ResponseEntity<TokenResponse> noStore(TokenResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new org.springframework.security.authentication.BadCredentialsException("Authentication token is missing");
        }
        return authorization.substring(7);
    }
}
