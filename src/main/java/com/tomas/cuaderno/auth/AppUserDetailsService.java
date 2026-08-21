package com.tomas.cuaderno.auth;

import com.tomas.cuaderno.common.security.AppPrincipal;
import java.util.UUID;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository repository;
    public AppUserDetailsService(UserRepository repository) { this.repository = repository; }
    public UserDetails loadUserByUsername(String username) {
        User user;
        try { user = repository.findByAuthUserId(UUID.fromString(username)).orElseThrow(() -> new UsernameNotFoundException("User not found")); }
        catch (IllegalArgumentException ex) { throw new UsernameNotFoundException("Invalid central user id", ex); }
        return new AppPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.getRole(), user.isEnabled());
    }
}
