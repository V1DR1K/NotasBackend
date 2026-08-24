package com.tomas.cuaderno.auth;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record UserResponse(UUID id, String username, String role, boolean mustChangePassword) {}
    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record MessageResponse(String message) {}
}
