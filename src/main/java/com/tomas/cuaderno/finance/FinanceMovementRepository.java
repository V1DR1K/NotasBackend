package com.tomas.cuaderno.finance;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

public interface FinanceMovementRepository extends JpaRepository<FinanceMovement, UUID>, JpaSpecificationExecutor<FinanceMovement> {
    Optional<FinanceMovement> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
    @Query("select m.bucket as bucket, coalesce(sum(m.amountArs), 0) as total from FinanceMovement m where m.ownerId = :owner and m.deletedAt is null and m.date between :from and :to group by m.bucket")
    List<SummaryRow> summarize(@Param("owner") UUID owner, @Param("from") LocalDate from, @Param("to") LocalDate to);
    interface SummaryRow { FinanceBucket getBucket(); BigDecimal getTotal(); }
}
