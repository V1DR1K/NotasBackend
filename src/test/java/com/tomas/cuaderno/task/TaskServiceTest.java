package com.tomas.cuaderno.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.NullNode;
import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.configuration.ConfigKind;
import com.tomas.cuaderno.configuration.ConfigurationDtos;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository repository;
    @Mock ConfigurationService configuration;
    @InjectMocks TaskService service;

    @Test
    void createTask_whenCategoryIsActive_shouldStartPending() {
        UUID owner = UUID.randomUUID();
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));
        when(configuration.projectCode(owner, "laburo")).thenReturn("laburo");
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(owner, new TaskDtos.CreateRequest("Entregar informe", "Revisar la conclusión", "laburo", null, LocalDate.of(2026, 10, 1), "laburo"));

        assertThat(result.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(result.title()).isEqualTo("Entregar informe");
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.projectCode()).isEqualTo("laburo");
        verify(configuration).requireActive(owner, ConfigKind.TASK_CATEGORY, "laburo", "categoryCode");
    }

    @Test
    void createTask_whenCreatedCompleted_shouldRecordCompletionTime() {
        UUID owner = UUID.randomUUID();
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(owner, new TaskDtos.CreateRequest("Entregar informe", null, "laburo", TaskStatus.COMPLETED, null, null));

        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    void createTask_whenCategoryIsInactive_shouldRejectRequest() {
        UUID owner = UUID.randomUUID();
        when(configuration.requireActive(eq(owner), eq(ConfigKind.TASK_CATEGORY), eq("otra"), eq("categoryCode")))
                .thenThrow(new BadRequestException("Unknown or inactive categoryCode"));

        assertThatThrownBy(() -> service.create(owner, new TaskDtos.CreateRequest("Leer", null, "otra", null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any(Task.class));
    }

    @Test
    void patchTask_whenStatusChanges_shouldAllowFreeMovementAndClearDueDate() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Task task = new Task();
        task.setOwnerId(owner);
        task.setStatus(TaskStatus.PENDING);
        task.setCategoryCode("laburo");
        task.setTitle("Tarea");
        task.setDueDate(LocalDate.of(2026, 9, 20));
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(repository.findById(id)).thenReturn(Optional.of(task));
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));

        var result = service.patch(owner, id, new TaskDtos.PatchRequest(null, null, null, TaskStatus.COMPLETED, NullNode.getInstance(), null));

        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.completedAt()).isNotNull();
        assertThat(result.dueDate()).isNull();
    }

    @Test
    void patchTask_whenCompletedTaskIsEdited_shouldPreserveCompletionTime() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-20T12:00:00Z");
        Task task = completedTask(owner, completedAt);
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(repository.findById(id)).thenReturn(Optional.of(task));
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));

        var result = service.patch(owner, id, new TaskDtos.PatchRequest("Título actualizado", null, null, null, null, null));

        assertThat(result.completedAt()).isEqualTo(completedAt);
    }

    @Test
    void patchTask_whenCompletedTaskIsReopened_shouldClearCompletionTime() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Task task = completedTask(owner, Instant.parse("2026-09-20T12:00:00Z"));
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(repository.findById(id)).thenReturn(Optional.of(task));
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));

        var result = service.patch(owner, id, new TaskDtos.PatchRequest(null, null, null, TaskStatus.IN_PROGRESS, null, null));

        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.completedAt()).isNull();
    }

    private Task completedTask(UUID owner, Instant completedAt) {
        Task task = new Task();
        task.setOwnerId(owner);
        task.setTitle("Tarea");
        task.setCategoryCode("laburo");
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(completedAt);
        return task;
    }

    @Test
    void listTasks_whenFilteringByDueDate_shouldReturnMatchingTaskResponses() {
        UUID owner = UUID.randomUUID();
        LocalDate dueDate = LocalDate.of(2026, 10, 15);
        Task task = new Task();
        task.setOwnerId(owner);
        task.setTitle("Entregar informe");
        task.setCategoryCode("laburo");
        task.setStatus(TaskStatus.COMPLETED);
        task.setDueDate(dueDate);
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        var pageable = PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "dueDate"));
        when(configuration.indexIncludingDeleted(owner, ConfigKind.TASK_CATEGORY)).thenReturn(Map.of("laburo", category));
        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(java.util.List.of(task), pageable, 1));

        var result = service.list(owner, null, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.content().getFirst().dueDate()).isEqualTo(dueDate);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }
}
