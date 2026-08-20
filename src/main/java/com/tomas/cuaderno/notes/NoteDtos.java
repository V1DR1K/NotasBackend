package com.tomas.cuaderno.notes;

import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;

public final class NoteDtos {
    private NoteDtos() {}
    public record CreateRequest(@NotBlank @Size(max = 180) String title, @NotBlank @Size(max = 10000) String body, @NotBlank @Size(max = 80) String categoryCode, @NotNull LocalDate date) {}
    public record PatchRequest(@Size(max = 180) String title, @Size(max = 10000) String body, @Size(max = 80) String categoryCode, LocalDate date) {}
    public record Response(UUID id, String title, String body, ConfigOptionResponse category, LocalDate date, Instant createdAt, Instant updatedAt) {}
}
