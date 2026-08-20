package com.tomas.cuaderno.day;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface DayEntryRepository extends JpaRepository<DayEntry, UUID>, JpaSpecificationExecutor<DayEntry> {
    Optional<DayEntry> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID owner);
    long countByOwnerIdAndDeletedAtIsNull(UUID owner);
}
