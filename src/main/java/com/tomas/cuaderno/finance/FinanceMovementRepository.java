package com.tomas.cuaderno.finance;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;

public interface FinanceMovementRepository extends JpaRepository<FinanceMovement, UUID>, JpaSpecificationExecutor<FinanceMovement> {
    Optional<FinanceMovement> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
