package com.tomas.cuaderno.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "central_auth_users")
public class CentralAuthUser {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CentralAuthUser() {}

    public static CentralAuthUser create(String username, String passwordHash, boolean mustChangePassword) {
        CentralAuthUser user = new CentralAuthUser();
        user.id = UUID.randomUUID();
        user.username = username;
        user.passwordHash = passwordHash;
        user.mustChangePassword = mustChangePassword;
        user.createdAt = Instant.now();
        return user;
    }

    public void setUsername(String value) { username = value; }
    public void changePassword(String value) { passwordHash = value; mustChangePassword = false; }
    public void setMustChangePassword(boolean value) { mustChangePassword = value; }
    public void setEnabled(boolean value) { enabled = value; }
    public void markLogin() { lastLoginAt = Instant.now(); }
    public void logicalDelete() { deletedAt = Instant.now(); enabled = false; }
    public String getStatus() { return deletedAt != null ? "DELETED" : enabled ? "ACTIVE" : "DISABLED"; }
    public boolean isActive() { return enabled && deletedAt == null; }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEnabled() { return enabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
