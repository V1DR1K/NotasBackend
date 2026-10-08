package com.tomas.cuaderno.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "central_user_app_access", uniqueConstraints =
        @UniqueConstraint(name = "uq_central_user_app_access", columnNames = {"user_id", "app_code"}))
public class CentralUserAppAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "app_code", nullable = false, length = 20, updatable = false)
    private String appCode;

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false)
    private boolean enabled;

    protected CentralUserAppAccess() {}

    public static CentralUserAppAccess create(UUID userId, String appCode, String role, boolean enabled) {
        CentralUserAppAccess access = new CentralUserAppAccess();
        access.userId = userId;
        access.appCode = appCode;
        access.role = role;
        access.enabled = enabled;
        return access;
    }

    public void update(String role, boolean enabled) { this.role = role; this.enabled = enabled; }
    public UUID getUserId() { return userId; }
    public String getAppCode() { return appCode; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
