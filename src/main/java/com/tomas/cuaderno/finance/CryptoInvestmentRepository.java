package com.tomas.cuaderno.finance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CryptoInvestmentRepository extends JpaRepository<CryptoInvestment, UUID> {
    List<CryptoInvestment> findByOwnerIdOrderByDateDescCreatedAtDesc(UUID ownerId);

    List<CryptoInvestment> findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(UUID ownerId);

    Optional<CryptoInvestment> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CryptoInvestment i where i.id = :id and i.ownerId = :owner and i.deletedAt is null")
    Optional<CryptoInvestment> findActiveForUpdate(@Param("id") UUID id, @Param("owner") UUID ownerId);
}
