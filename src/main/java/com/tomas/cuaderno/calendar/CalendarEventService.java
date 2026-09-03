package com.tomas.cuaderno.calendar;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.ConfigKind;
import com.tomas.cuaderno.configuration.ConfigurationDtos;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarEventService {
    private final CalendarEventRepository repository;
    private final ConfigurationService configuration;

    public CalendarEventService(CalendarEventRepository repository, ConfigurationService configuration) {
        this.repository = repository;
        this.configuration = configuration;
    }

    public PageResponse<CalendarEventDtos.Response> list(UUID owner, LocalDate date, LocalDate from, LocalDate to, String categoryCode, Pageable pageable) {
        Specification<CalendarEvent> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("ownerId"), owner),
                cb.isNull(root.get("deletedAt")));
        if (date != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("date"), date));
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("date"), to));
        if (categoryCode != null && !categoryCode.isBlank()) {
            String normalized = categoryCode.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("categoryCode")), normalized));
        }
        Map<String, ConfigurationDtos.ConfigOptionResponse> categories = configuration.indexIncludingDeleted(owner, ConfigKind.EVENT_CATEGORY);
        return PageResponse.from(repository.findAll(spec, pageable).map(event -> response(event, categories)));
    }

    public CalendarEventDtos.Response get(UUID owner, UUID id) {
        CalendarEvent event = find(owner, id);
        return response(event, configuration.indexIncludingDeleted(owner, ConfigKind.EVENT_CATEGORY));
    }

    @Transactional
    public CalendarEventDtos.Response create(UUID owner, CalendarEventDtos.CreateRequest request) {
        CalendarEvent event = new CalendarEvent();
        event.setOwnerId(owner);
        event.setDate(request.date());
        event.setDescription(request.description().trim());
        event.setCategoryCode(normalizeAndValidateCategory(owner, request.categoryCode()));
        return response(owner, repository.save(event));
    }

    @Transactional
    public CalendarEventDtos.Response patch(UUID owner, UUID id, CalendarEventDtos.PatchRequest request) {
        CalendarEvent event = find(owner, id);
        if (request.date() != null) event.setDate(request.date());
        if (request.description() != null) {
            if (request.description().isBlank()) throw new BadRequestException("description cannot be blank");
            event.setDescription(request.description().trim());
        }
        if (request.categoryCode() != null) event.setCategoryCode(normalizeAndValidateCategory(owner, request.categoryCode()));
        return response(owner, event);
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        CalendarEvent event = find(owner, id);
        event.setDeletedAt(Instant.now());
    }

    private CalendarEvent find(UUID owner, UUID id) {
        return repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner)
                .orElseThrow(() -> new NotFoundException("Calendar event not found"));
    }

    private String normalizeAndValidateCategory(UUID owner, String code) {
        configuration.requireActive(owner, ConfigKind.EVENT_CATEGORY, code, "categoryCode");
        return code.trim();
    }

    private CalendarEventDtos.Response response(UUID owner, CalendarEvent event) {
        return response(event, configuration.indexIncludingDeleted(owner, ConfigKind.EVENT_CATEGORY));
    }

    private CalendarEventDtos.Response response(CalendarEvent event, Map<String, ConfigurationDtos.ConfigOptionResponse> categories) {
        ConfigurationDtos.ConfigOptionResponse category = categories.get(event.getCategoryCode().toLowerCase(Locale.ROOT));
        if (category == null) throw new NotFoundException("Configuration option not found");
        return new CalendarEventDtos.Response(event.getId(), event.getDate(), event.getDescription(), category, event.getCreatedAt(), event.getUpdatedAt());
    }
}
