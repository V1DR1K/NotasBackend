package com.tomas.cuaderno.day;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface DayEntryRepository extends JpaRepository<DayEntry, UUID>, JpaSpecificationExecutor<DayEntry> {
    Optional<DayEntry> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    Optional<DayEntry> findByOwnerIdAndDateAndDeletedAtIsNull(UUID owner, java.time.LocalDate date);
    long countByOwnerIdAndDeletedAtIsNullAndDateBetween(UUID owner, java.time.LocalDate from, java.time.LocalDate to);
    long countByOwnerIdAndDeletedAtIsNullAndDateBetweenAndAnalysisStatus(UUID owner, java.time.LocalDate from, java.time.LocalDate to, DayAnalysisStatus status);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
