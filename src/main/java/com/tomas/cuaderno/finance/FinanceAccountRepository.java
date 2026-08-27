package com.tomas.cuaderno.finance;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceAccountRepository extends JpaRepository<FinanceAccount, UUID> {
    List<FinanceAccount> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    List<FinanceAccount> findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(UUID ownerId);
    Optional<FinanceAccount> findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(UUID ownerId, String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinanceAccount a where a.ownerId = :owner and lower(a.code) = lower(:code) and a.deletedAt is null and a.active = true")
    Optional<FinanceAccount> findActiveForUpdate(@Param("owner") UUID owner, @Param("code") String code);
}
