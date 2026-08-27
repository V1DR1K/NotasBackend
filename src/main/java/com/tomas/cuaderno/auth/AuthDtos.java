package com.tomas.cuaderno.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}
    public record LoginRequest(@NotBlank @Size(max = 120) String username, @NotBlank @Size(max = 256) String password) {}
    public record UserResponse(UUID id, String username, String role, boolean mustChangePassword) {}
    public record AuthResponse(@JsonIgnore String accessToken, @JsonIgnore String refreshToken, @JsonIgnore String tokenType, @JsonIgnore long expiresIn, UserResponse user) {
        public AuthResponse(UserResponse user) { this(null, null, null, 0, user); }
    }
    public record RefreshRequest(@NotBlank @Size(max = 4096) String refreshToken) {}
    public record ChangePasswordRequest(@NotBlank @Size(max = 256) String currentPassword, @NotBlank @Size(min = 12, max = 256) String newPassword) {}
    public record MessageResponse(String message) {}
}
