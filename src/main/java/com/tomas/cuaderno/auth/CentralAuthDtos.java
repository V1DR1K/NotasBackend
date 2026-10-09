package com.tomas.cuaderno.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CentralAuthDtos {
    private CentralAuthDtos() {}

    public record Credentials(@NotBlank @Size(max = 80) String username,
                              @NotBlank @Size(max = 128) String password,
                              @NotBlank @Size(max = 20) String clientApp) {}
    public record RegisterRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                  @NotBlank @Size(min = 10, max = 128) String password,
                                  @Size(max = 20) String clientApp) {}
    public record RefreshRequest(@NotBlank @Size(max = 4096) String refreshToken,
                                 @NotBlank @Size(max = 20) String clientApp) {}
    public record LogoutRequest(@NotBlank @Size(max = 4096) String refreshToken,
                                @Size(max = 20) String clientApp) {}
    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(min = 10, max = 128) String newPassword,
                                        @NotBlank @Size(max = 20) String clientApp) {}
    public record CentralUser(UUID id, String username, String status, Instant created,
                              Instant lastLogin, boolean mustChangePassword, String role) {}
    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresIn, CentralUser user) {}
    public record RegistrationResponse(UUID id, String username, String requestedApp,
                                       CentralAppAccessStatus accessStatus, String message) {}
    public record MeResponse(UUID id, String username, boolean mustChangePassword, String role) {}
    public record MessageResponse(String message) {}

    public record AppAccessRequest(@NotNull Boolean enabled, @NotBlank @Size(max = 20) String role,
                                   CentralAppAccessStatus status) {}
    public record UpdateApplicationsRequest(@NotNull Map<String, AppAccessRequest> applications) {}
    public record AppAccessResponse(String appCode, String role, boolean enabled,
                                    CentralAppAccessStatus status) {}
    public record UserAdminResponse(UUID id, String username, String status, Instant created,
                                    Instant lastLogin, boolean mustChangePassword,
                                    List<AppAccessResponse> applications) {}

    public record CreateUserRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                    @NotBlank @Size(min = 10, max = 128) String password,
                                    Boolean mustChangePassword) {}
    public record UpdateUserRequest(@Size(min = 3, max = 80) String username,
                                    @Size(min = 10, max = 128) String password,
                                    Boolean enabled,
                                    Boolean mustChangePassword) {}
}
