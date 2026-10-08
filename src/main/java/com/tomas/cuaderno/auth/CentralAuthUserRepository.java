package com.tomas.cuaderno.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentralAuthUserRepository extends JpaRepository<CentralAuthUser, UUID> {
    Optional<CentralAuthUser> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);
    List<CentralAuthUser> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    boolean existsByUsernameIgnoreCase(String username);
}
