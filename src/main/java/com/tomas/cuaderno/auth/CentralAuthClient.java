package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.errors.BadRequestException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;

@Service
public class CentralAuthClient {
    private static final Logger log = LoggerFactory.getLogger(CentralAuthClient.class);
    private final RestClient client;

    public CentralAuthClient(AuthProperties properties) {
        if (properties.getServiceUrl() == null || properties.getServiceUrl().isBlank()) throw new IllegalStateException("AUTH_SERVICE_URL is required");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getClientTimeoutMs());
        factory.setReadTimeout(properties.getClientTimeoutMs());
        client = RestClient.builder().requestFactory(factory).baseUrl(properties.getServiceUrl().replaceAll("/$", "")).build();
    }

    public TokenResponse login(String username, String password) {
        try {
            return client.post().uri("/api/login").body(new Credentials(username, password)).retrieve().body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw new org.springframework.security.authentication.BadCredentialsException("Central authentication failed");
        }
    }

    public TokenResponse refresh(String refreshToken) {
        try {
            return client.post().uri("/api/refresh").body(new RefreshRequest(refreshToken)).retrieve().body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            log.debug("Central refresh rejected with status {}", ex.getStatusCode().value());
            throw new org.springframework.security.authentication.BadCredentialsException("Central refresh failed");
        } catch (RuntimeException ex) {
            throw new AuthenticationServiceException("Central authentication service is unavailable", ex);
        }
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            client.post().uri("/api/logout").body(new RefreshRequest(refreshToken)).retrieve().toBodilessEntity();
        } catch (RuntimeException ex) {
            // Logout must remain idempotent: clearing the local cookies is enough
            // to end this app session even when central revocation is unavailable
            // or the refresh token has already expired.
            log.debug("Central logout could not revoke the refresh token", ex);
        }
    }

    public CentralUser me(String accessToken) {
        try {
            return client.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)).retrieve().body(CentralUser.class);
        } catch (RestClientResponseException ex) {
            throw new org.springframework.security.authentication.BadCredentialsException("Central session is invalid");
        }
    }

    public void changePassword(String accessToken, String currentPassword, String newPassword) {
        try {
            client.post().uri("/api/change-password").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                    .body(new ChangePasswordRequest(currentPassword, newPassword)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new BadRequestException("No se pudo cambiar la contraseña");
        }
    }

    private String bearer(String token) { return "Bearer " + token; }

    public record Credentials(String username, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record CentralUser(UUID id, String username, boolean mustChangePassword) {}
    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, CentralUser user) {}
}
