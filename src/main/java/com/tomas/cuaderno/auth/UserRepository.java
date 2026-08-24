package com.tomas.cuaderno.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    List<User> findByAuthUserIdIsNotNull();
    Optional<User> findByUsername(String username);
    Optional<User> findByAuthUserId(UUID authUserId);
}
