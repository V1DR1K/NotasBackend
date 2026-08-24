package com.tomas.cuaderno.finance;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, UUID> {
    List<FinanceAccount> findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(UUID ownerId);
    Optional<FinanceAccount> findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(UUID ownerId, String code);
}
