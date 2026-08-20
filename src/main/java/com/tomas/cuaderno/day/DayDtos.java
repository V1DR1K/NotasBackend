package com.tomas.cuaderno.day;

import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class DayDtos {
    private DayDtos() {}
    public record CreateRequest(@NotNull LocalDate date, @NotBlank @Size(max = 80) String statusCode, @NotBlank @Size(max = 120) String feeling, @NotBlank @Size(max = 3000) String description) {}
    public record PatchRequest(LocalDate date, @Size(max = 80) String statusCode, @Size(max = 120) String feeling, @Size(max = 3000) String description) {}
    public record Response(UUID id, LocalDate date, ConfigOptionResponse status, String feeling, String description, Instant createdAt, Instant updatedAt) {}
}
