package com.tomas.cuaderno.auth;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalUserProvisioningService {
    private final UserRepository users;
    private final InitialDataSeeder seeder;
    private final AuthProperties properties;

    public LocalUserProvisioningService(UserRepository users, InitialDataSeeder seeder, AuthProperties properties) {
        this.users = users;
        this.seeder = seeder;
        this.properties = properties;
    }

    @Transactional
    public User provision(CentralAuthClient.CentralUser centralUser) {
        User user = users.findByAuthUserId(centralUser.id()).orElseGet(() -> findUnmappedLegacyUser(centralUser.username()));
        if (user == null) {
            user = new User();
            user.setUsername(centralUser.username());
            user.setRole(properties.getDefaultRole().trim().toUpperCase());
        } else {
            user.setUsername(centralUser.username());
        }
        user.setAuthUserId(centralUser.id());
        user.setPasswordHash(null);
        user = users.save(user);
        seeder.seedFor(user);
        return user;
    }

    private User findUnmappedLegacyUser(String username) {
        User candidate = users.findByUsername(username).orElse(null);
        if (candidate == null && "tomas".equalsIgnoreCase(username)) candidate = users.findByUsername("tomas tomas").orElse(null);
        return candidate != null && candidate.getAuthUserId() == null ? candidate : null;
    }
}
