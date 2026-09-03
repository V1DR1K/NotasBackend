package com.tomas.cuaderno.calendar;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID>, JpaSpecificationExecutor<CalendarEvent> {
    Optional<CalendarEvent> findByIdAndOwnerIdAndDeletedAtIsNull(UUID id, UUID ownerId);
}
