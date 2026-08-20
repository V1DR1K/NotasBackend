package com.tomas.cuaderno.common.security;

import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.List;

public record AppPrincipal(UUID id, String username, String password, String role, boolean enabled) implements UserDetails {
    public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    public String getPassword() { return password; }
    public String getUsername() { return username; }
    public boolean isEnabled() { return enabled; }
}
