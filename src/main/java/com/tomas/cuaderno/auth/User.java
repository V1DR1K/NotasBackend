package com.tomas.cuaderno.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "app_users")
public class User {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String username;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false, length = 20) private String role;
    @Column(nullable = false) private boolean enabled = true;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;
    @PrePersist void create() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
    public UUID getId() { return id; } public String getUsername() { return username; } public void setUsername(String v) { username = v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { passwordHash = v; }
    public String getRole() { return role; } public void setRole(String v) { role = v; } public boolean isEnabled() { return enabled; }
}
