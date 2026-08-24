package com.tomas.cuaderno.day;

import com.tomas.cuaderno.ai.AiUnavailableException;
import com.tomas.cuaderno.ai.GeminiDaySuggestionService;
import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.ConfigKind;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DayService {
    private final DayEntryRepository repository;
    private final ConfigurationService configuration;
    private final GeminiDaySuggestionService analysis;

    public DayService(DayEntryRepository repository, ConfigurationService configuration, GeminiDaySuggestionService analysis) {
        this.repository = repository;
        this.configuration = configuration;
        this.analysis = analysis;
    }

    public PageResponse<DayDtos.Response> list(UUID owner, LocalDate date, LocalDate from, LocalDate to, String statusCode, List<String> feelings, Pageable pageable) {
        Specification<DayEntry> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (date != null) spec = spec.and((r, q, c) -> c.equal(r.get("date"), date));
        if (from != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("date"), from));
        if (to != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("date"), to));
        if (statusCode != null) spec = spec.and((r, q, c) -> c.equal(c.lower(r.get("statusCode")), statusCode.toLowerCase()));
        if (feelings != null && !feelings.isEmpty()) {
            var codes = feelings.stream().filter(code -> code != null && !code.isBlank()).map(String::toLowerCase).distinct().toList();
            if (!codes.isEmpty()) spec = spec.and((r, q, c) -> c.or(codes.stream().map(code -> c.like(c.lower(r.get("feeling")), "%|" + code + "|%")).toArray(jakarta.persistence.criteria.Predicate[]::new)));
        }
        return PageResponse.from(repository.findAll(spec, pageable).map(x -> response(owner, x)));
    }

    public DayDtos.Response get(UUID owner, UUID id) {
        return response(owner, repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Day entry not found")));
    }

    public long count(UUID owner) { return repository.countByOwnerIdAndDeletedAtIsNull(owner); }

    @Transactional
    public DayDtos.Response create(UUID owner, DayDtos.CreateRequest request) {
        DayEntry item = new DayEntry();
        item.setOwnerId(owner);
        item.setDate(request.date());
        item.setDescription(request.description().trim());
        markPending(item);
        return response(owner, repository.save(item));
    }

    @Transactional
    public DayDtos.Response patch(UUID owner, UUID id, DayDtos.PatchRequest request) {
        DayEntry item = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Day entry not found"));
        if (request.date() != null) item.setDate(request.date());
        if (request.description() != null) {
            if (request.description().isBlank()) throw new com.tomas.cuaderno.common.errors.BadRequestException("description cannot be blank");
            String description = request.description().trim();
            if (!description.equals(item.getDescription())) {
                item.setDescription(description);
                markPending(item);
            }
        }
        return response(owner, item);
    }

    @Transactional
    public DayDtos.Response analyze(UUID owner, UUID id) {
        DayEntry item = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Day entry not found"));
        try {
            var suggestion = analysis.suggest(owner, item.getDescription());
            if (suggestion.analyzed()) {
                item.setStatusCode(suggestion.statusCode());
                item.setFeeling(serializeFeelings(suggestion.feelingCodes()));
                item.setAnalysisStatus(DayAnalysisStatus.COMPLETED);
            } else {
                markPending(item);
            }
        } catch (AiUnavailableException ex) {
            markPending(item);
        }
        return response(owner, item);
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        DayEntry item = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Day entry not found"));
        item.setDeletedAt(Instant.now());
    }

    private void markPending(DayEntry item) {
        item.setAnalysisStatus(DayAnalysisStatus.PENDING);
        item.setStatusCode(null);
        item.setFeeling(null);
    }

    private String serializeFeelings(List<String> values) { return "|" + String.join("|", values) + "|"; }

    private DayDtos.Response response(UUID owner, DayEntry item) {
        return new DayDtos.Response(item.getId(), item.getDate(), item.getAnalysisStatus(), item.getStatusCode() == null ? null : configuration.option(owner, ConfigKind.DAY_STATUS, item.getStatusCode()), item.getFeeling(), item.getDescription(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
