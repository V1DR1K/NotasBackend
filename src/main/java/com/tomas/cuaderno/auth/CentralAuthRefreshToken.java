package com.tomas.cuaderno.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "central_auth_refresh_tokens")
public class CentralAuthRefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private CentralAuthUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "client_app", length = 20)
    private String clientApp;

    protected CentralAuthRefreshToken() {}

    public static CentralAuthRefreshToken create(CentralAuthUser user, String tokenHash, Instant expiresAt, String clientApp) {
        CentralAuthRefreshToken token = new CentralAuthRefreshToken();
        token.user = user;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.createdAt = Instant.now();
        token.clientApp = clientApp;
        return token;
    }

    public void revoke() { if (revokedAt == null) revokedAt = Instant.now(); }
    public boolean usable() { return revokedAt == null && expiresAt.isAfter(Instant.now()); }
    public boolean belongsToApp(String appCode) { return clientApp == null || clientApp.equals(appCode); }
    public CentralAuthUser getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getClientApp() { return clientApp; }
}
