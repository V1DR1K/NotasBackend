package com.tomas.cuaderno.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CentralAuthRefreshTokenRepository extends JpaRepository<CentralAuthRefreshToken, UUID> {
    Optional<CentralAuthRefreshToken> findByTokenHash(String tokenHash);
    List<CentralAuthRefreshToken> findAllByUser(CentralAuthUser user);
}
