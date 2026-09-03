package com.tomas.cuaderno.calendar;

import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class CalendarEventDtos {
    private CalendarEventDtos() {}

    public record CreateRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 80) String categoryCode) {}

    public record PatchRequest(
            LocalDate date,
            @Size(max = 1000) String description,
            @Size(max = 80) String categoryCode) {}

    public record Response(
            UUID id,
            LocalDate date,
            String description,
            ConfigOptionResponse category,
            Instant createdAt,
            Instant updatedAt) {}
}
