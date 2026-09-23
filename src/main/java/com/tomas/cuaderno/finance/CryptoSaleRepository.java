package com.tomas.cuaderno.finance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CryptoSaleRepository extends JpaRepository<CryptoSale, UUID> {
    List<CryptoSale> findByInvestmentIdInOrderByDateDescCreatedAtDesc(Collection<UUID> investmentIds);

    List<CryptoSale> findByInvestmentIdAndDeletedAtIsNull(UUID investmentId);

    Optional<CryptoSale> findByIdAndInvestmentIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID investmentId, UUID ownerId);
}
