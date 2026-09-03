package com.tomas.cuaderno.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CryptoInvestmentRepository extends JpaRepository<CryptoInvestment, UUID> {
    List<CryptoInvestment> findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(UUID ownerId);

    Optional<CryptoInvestment> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);

    @Query("select coalesce(sum(i.amountArs), 0) from CryptoInvestment i where i.ownerId = :owner and i.deletedAt is null")
    BigDecimal sumAmountArs(@Param("owner") UUID ownerId);

    @Query("select coalesce(sum(i.amountUsd), 0) from CryptoInvestment i where i.ownerId = :owner and i.deletedAt is null")
    BigDecimal sumAmountUsd(@Param("owner") UUID ownerId);

    @Query("select i.asset as asset, coalesce(sum(i.amountUsd), 0) as amountUsd, coalesce(sum(i.amountArs), 0) as amountArs, count(i.id) as purchases from CryptoInvestment i where i.ownerId = :owner and i.deletedAt is null group by i.asset order by sum(i.amountUsd) desc")
    List<PositionRow> summarizePositions(@Param("owner") UUID ownerId);

    interface PositionRow {
        CryptoAsset getAsset();
        BigDecimal getAmountUsd();
        BigDecimal getAmountArs();
        long getPurchases();
    }
}
