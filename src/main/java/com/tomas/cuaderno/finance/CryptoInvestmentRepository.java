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
    @Query("select coalesce(sum(i.amountArs), 0) - coalesce((select sum(s.costBasisArs) from CryptoSale s, CryptoInvestment p where s.investmentId = p.id and p.ownerId = :owner and p.deletedAt is null and s.deletedAt is null), 0) from CryptoInvestment i where i.ownerId = :owner and i.deletedAt is null")
    java.math.BigDecimal openCostBasisArs(@Param("owner") UUID ownerId);
    @Query("select coalesce(sum(i.amountUsd), 0) - coalesce((select sum(s.costBasisUsd) from CryptoSale s, CryptoInvestment p where s.investmentId = p.id and p.ownerId = :owner and p.deletedAt is null and s.deletedAt is null), 0) from CryptoInvestment i where i.ownerId = :owner and i.deletedAt is null")
    java.math.BigDecimal openCostBasisUsd(@Param("owner") UUID ownerId);
    List<CryptoInvestment> findByOwnerIdOrderByDateDescCreatedAtDesc(UUID ownerId);

    List<CryptoInvestment> findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(UUID ownerId);

    Optional<CryptoInvestment> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CryptoInvestment i where i.id = :id and i.ownerId = :owner and i.deletedAt is null")
    Optional<CryptoInvestment> findActiveForUpdate(@Param("id") UUID id, @Param("owner") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CryptoInvestment i where i.ownerId = :owner and i.asset = :asset and i.deletedAt is null order by i.date asc, i.createdAt asc")
    List<CryptoInvestment> findActiveForUpdate(@Param("owner") UUID ownerId, @Param("asset") CryptoAsset asset);
}
