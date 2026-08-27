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
    @Query("select m.bucket as bucket, coalesce(sum(m.amountArs), 0) as total from FinanceMovement m where m.ownerId = :owner and m.deletedAt is null and lower(m.accountCode) = 'mercadopago' and m.date between :from and :to group by m.bucket")
    List<SummaryRow> summarize(@Param("owner") UUID owner, @Param("from") LocalDate from, @Param("to") LocalDate to);
    @Query("select m.date as date, m.bucket as bucket, coalesce(sum(m.amountArs), 0) as total from FinanceMovement m where m.ownerId = :owner and m.deletedAt is null and m.date between :from and :to group by m.date, m.bucket order by m.date asc")
    List<DailySummaryRow> summarizeDaily(@Param("owner") UUID owner, @Param("from") LocalDate from, @Param("to") LocalDate to);
    @Query("select m.bucket as bucket, m.itemCode as itemCode, coalesce(sum(m.amountArs), 0) as total from FinanceMovement m where m.ownerId = :owner and m.deletedAt is null and m.date between :from and :to group by m.bucket, m.itemCode order by m.bucket asc, sum(m.amountArs) desc")
    List<CategorySummaryRow> summarizeCategories(@Param("owner") UUID owner, @Param("from") LocalDate from, @Param("to") LocalDate to);
    interface SummaryRow { FinanceBucket getBucket(); BigDecimal getTotal(); }
    interface DailySummaryRow { LocalDate getDate(); FinanceBucket getBucket(); BigDecimal getTotal(); }
    interface CategorySummaryRow { FinanceBucket getBucket(); String getItemCode(); BigDecimal getTotal(); }
}
