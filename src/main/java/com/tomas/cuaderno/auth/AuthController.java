package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.security.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager; private final JwtService jwt; private final SecurityProperties properties;
    public AuthController(AuthenticationManager authenticationManager, JwtService jwt, SecurityProperties properties) { this.authenticationManager = authenticationManager; this.jwt = jwt; this.properties = properties; }
    @PostMapping("/login")
    public AuthDtos.UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        var authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        var principal = (AppPrincipal) authentication.getPrincipal();
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), jwt.create(principal)).httpOnly(true).secure(properties.isSecureCookie()).sameSite("Lax").path("/").maxAge(properties.getExpiration()).build();
        response.addHeader("Set-Cookie", cookie.toString());
        return new AuthDtos.UserResponse(principal.id(), principal.username(), principal.role());
    }
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(properties.getCookieName(), "").httpOnly(true).secure(properties.isSecureCookie()).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
    @GetMapping("/me")
    public AuthDtos.UserResponse me(@AuthenticationPrincipal AppPrincipal principal) { return new AuthDtos.UserResponse(principal.id(), principal.username(), principal.role()); }
    @GetMapping("/csrf")
    public AuthDtos.CsrfResponse csrf(CsrfToken token) { return new AuthDtos.CsrfResponse(token.getToken()); }
}
