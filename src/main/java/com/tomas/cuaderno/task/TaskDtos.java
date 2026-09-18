package com.tomas.cuaderno.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 10000) String detail,
            @NotBlank @Size(max = 80) String categoryCode,
            TaskStatus status,
            LocalDate dueDate) {}

    public record PatchRequest(
            @Size(max = 180) String title,
            @Size(max = 10000) String detail,
            @Size(max = 80) String categoryCode,
            TaskStatus status,
            JsonNode dueDate) {}

    public record Response(
            UUID id,
            String title,
            String detail,
            TaskStatus status,
            ConfigOptionResponse category,
            LocalDate dueDate,
            Instant createdAt,
            Instant updatedAt) {}

    public record Stats(long pending, long inProgress, long completed, long overdue) {}

    public record DashboardSummary(Stats stats, List<Response> upcoming, List<Response> recent) {}
}
