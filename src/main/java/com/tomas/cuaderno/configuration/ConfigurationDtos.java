package com.tomas.cuaderno.configuration;

import jakarta.validation.constraints.*;

public final class ConfigurationDtos {
    private ConfigurationDtos() {}
    public record DayStatusRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String label, @Size(max = 16) String emoji, @Min(0) int sortOrder) {}
    public record OptionRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String label, @Min(0) int sortOrder, boolean active, FinanceItemType financeType, @Size(max = 80) String projectCode) {}
    public record PatchRequest(@Size(max = 160) String label, @Size(max = 16) String emoji, @Min(0) Integer sortOrder, Boolean active, FinanceItemType financeType, @Size(max = 80) String projectCode) {}
    public record CategoryRequest(@NotBlank @Size(max = 80) String code, @NotBlank @Size(max = 160) String label, @Min(0) int sortOrder, boolean active, @NotBlank @Size(max = 80) String projectCode) {}
    public record ConfigOptionResponse(String code, String label, String emoji, int sortOrder, boolean active, FinanceItemType financeType) {}
    public record CategoryResponse(java.util.UUID id, String code, String label, int sortOrder, boolean active, String projectCode) {}
}
