package com.tomas.cuaderno.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.ConfigKind;
import com.tomas.cuaderno.configuration.ConfigurationDtos;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final TaskRepository repository;
    private final ConfigurationService configuration;

    public TaskService(TaskRepository repository, ConfigurationService configuration) {
        this.repository = repository;
        this.configuration = configuration;
    }

    public PageResponse<TaskDtos.Response> list(UUID owner, TaskStatus status, String categoryCode, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Task> spec = activeFor(owner);
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        if (categoryCode != null && !categoryCode.isBlank()) {
            String normalized = categoryCode.trim().toLowerCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("categoryCode")), normalized));
        }
        if (from != null) spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), from));
        if (to != null) spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), to));
        Map<String, ConfigurationDtos.ConfigOptionResponse> categories = configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY);
        return PageResponse.from(repository.findAll(spec, pageable).map(task -> response(task, categories)));
    }

    public TaskDtos.Response get(UUID owner, UUID id) {
        return response(find(owner, id), configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY));
    }

    @Transactional
    public TaskDtos.Response create(UUID owner, TaskDtos.CreateRequest request) {
        configuration.requireActive(owner, ConfigKind.TASK_CATEGORY, request.categoryCode(), "categoryCode");
        Task task = new Task();
        task.setOwnerId(owner);
        task.setTitle(request.title().trim());
        task.setDetail(cleanDetail(request.detail()));
        task.setStatus(request.status() == null ? TaskStatus.PENDING : request.status());
        task.setCategoryCode(normalize(request.categoryCode()));
        task.setDueDate(request.dueDate());
        Task saved = repository.save(task);
        return response(saved, configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY));
    }

    @Transactional
    public TaskDtos.Response patch(UUID owner, UUID id, TaskDtos.PatchRequest request) {
        Task task = find(owner, id);
        if (request.title() != null) {
            if (request.title().isBlank()) throw new BadRequestException("title cannot be blank");
            task.setTitle(request.title().trim());
        }
        if (request.detail() != null) task.setDetail(cleanDetail(request.detail()));
        if (request.categoryCode() != null) {
            configuration.requireActive(owner, ConfigKind.TASK_CATEGORY, request.categoryCode(), "categoryCode");
            task.setCategoryCode(normalize(request.categoryCode()));
        }
        if (request.status() != null) task.setStatus(request.status());
        if (request.dueDate() != null) task.setDueDate(parseDueDate(request.dueDate()));
        return response(task, configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY));
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        Task task = find(owner, id);
        task.setDeletedAt(Instant.now());
    }

    public long count(UUID owner) {
        return repository.count(activeFor(owner));
    }

    public TaskDtos.DashboardSummary dashboard(UUID owner, LocalDate today) {
        long pending = repository.countByOwnerIdAndStatusAndDeletedAtIsNull(owner, TaskStatus.PENDING);
        long inProgress = repository.countByOwnerIdAndStatusAndDeletedAtIsNull(owner, TaskStatus.IN_PROGRESS);
        long completed = repository.countByOwnerIdAndStatusAndDeletedAtIsNull(owner, TaskStatus.COMPLETED);
        Specification<Task> open = activeFor(owner).and((root, query, cb) -> root.get("status").in(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
        List<Task> openTasks = repository.findAll(open, PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "dueDate").and(Sort.by(Sort.Direction.DESC, "updatedAt")))).getContent();
        long overdue = openTasks.stream().filter(task -> task.getDueDate() != null && task.getDueDate().isBefore(today)).count();
        Map<String, ConfigurationDtos.ConfigOptionResponse> categories = configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY);
        Comparator<Task> dueOrder = Comparator.comparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Task::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        List<TaskDtos.Response> upcoming = openTasks.stream().sorted(dueOrder).limit(5).map(task -> response(task, categories)).toList();
        List<TaskDtos.Response> recent = repository.findAll(activeFor(owner), PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "updatedAt"))).getContent().stream().map(task -> response(task, categories)).toList();
        return new TaskDtos.DashboardSummary(new TaskDtos.Stats(pending, inProgress, completed, overdue), upcoming, recent);
    }

    private Specification<Task> activeFor(UUID owner) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
    }

    private Task find(UUID owner, UUID id) {
        return repository.findById(id).filter(task -> owner.equals(task.getOwnerId()) && task.getDeletedAt() == null).orElseThrow(() -> new NotFoundException("Task not found"));
    }

    private TaskDtos.Response response(Task task, Map<String, ConfigurationDtos.ConfigOptionResponse> categories) {
        ConfigurationDtos.ConfigOptionResponse category = categories.get(task.getCategoryCode().toLowerCase(Locale.ROOT));
        if (category == null) throw new NotFoundException("Configuration option not found");
        return new TaskDtos.Response(task.getId(), task.getTitle(), task.getDetail(), task.getStatus(), category, task.getDueDate(), task.getCreatedAt(), task.getUpdatedAt());
    }

    private String normalize(String value) { return value.trim(); }
    private String cleanDetail(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private LocalDate parseDueDate(JsonNode value) {
        if (value.isNull()) return null;
        if (!value.isTextual()) throw new BadRequestException("dueDate must be a date or null");
        try { return LocalDate.parse(value.textValue()); }
        catch (RuntimeException exception) { throw new BadRequestException("dueDate must be a valid date"); }
    }
}
