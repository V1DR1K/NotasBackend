package com.tomas.cuaderno.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserResponse(UUID id, String username, String role) {}
    public record CsrfResponse(String token) {}
}
