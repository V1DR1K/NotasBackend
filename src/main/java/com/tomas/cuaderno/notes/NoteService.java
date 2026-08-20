package com.tomas.cuaderno.notes;

import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class NoteService {
    private final NoteRepository repository; private final ConfigurationService configuration;
    public NoteService(NoteRepository repository, ConfigurationService configuration) { this.repository = repository; this.configuration = configuration; }
    public PageResponse<NoteDtos.Response> list(UUID owner, String categoryCode, LocalDate date, LocalDate from, LocalDate to, String search, Pageable page) {
        Specification<Note> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (categoryCode != null) spec = spec.and((r, q, c) -> c.equal(c.lower(r.get("categoryCode")), categoryCode.toLowerCase()));
        if (date != null) spec = spec.and((r, q, c) -> c.equal(r.get("date"), date));
        if (from != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("date"), from));
        if (to != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("date"), to));
        if (search != null && !search.isBlank()) spec = spec.and((r, q, c) -> c.or(c.like(c.lower(r.get("title")), "%" + search.toLowerCase() + "%"), c.like(c.lower(r.get("body")), "%" + search.toLowerCase() + "%")));
        return PageResponse.from(repository.findAll(spec, page).map(x -> response(owner, x)));
    }
    public NoteDtos.Response get(UUID owner, UUID id) { return response(owner, repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Note not found"))); }
    public long count(UUID owner) { return repository.countByOwnerIdAndDeletedAtIsNull(owner); }
    @Transactional public NoteDtos.Response create(UUID owner, NoteDtos.CreateRequest request) { validateCategory(owner, request.categoryCode()); Note item = new Note(); item.setOwnerId(owner); item.setTitle(request.title().trim()); item.setBody(request.body().trim()); item.setCategoryCode(normalize(request.categoryCode())); item.setDate(request.date()); return response(owner, repository.save(item)); }
    @Transactional public NoteDtos.Response patch(UUID owner, UUID id, NoteDtos.PatchRequest request) { Note item = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Note not found")); if (request.categoryCode() != null) { validateCategory(owner, request.categoryCode()); item.setCategoryCode(normalize(request.categoryCode())); } if (request.title() != null) { if (request.title().isBlank()) throw new com.tomas.cuaderno.common.errors.BadRequestException("title cannot be blank"); item.setTitle(request.title().trim()); } if (request.body() != null) { if (request.body().isBlank()) throw new com.tomas.cuaderno.common.errors.BadRequestException("body cannot be blank"); item.setBody(request.body().trim()); } if (request.date() != null) item.setDate(request.date()); return response(owner, item); }
    @Transactional public void delete(UUID owner, UUID id) { Note item = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Note not found")); item.setDeletedAt(Instant.now()); }
    private void validateCategory(UUID owner, String code) { configuration.requireActive(owner, ConfigKind.NOTE_CATEGORY, code, "categoryCode"); }
    private String normalize(String value) { return value == null ? null : value.trim(); }
    private NoteDtos.Response response(UUID owner, Note item) { return new NoteDtos.Response(item.getId(), item.getTitle(), item.getBody(), item.getCategoryCode() == null ? null : configuration.option(owner, ConfigKind.NOTE_CATEGORY, item.getCategoryCode()), item.getDate(), item.getCreatedAt(), item.getUpdatedAt()); }
}
