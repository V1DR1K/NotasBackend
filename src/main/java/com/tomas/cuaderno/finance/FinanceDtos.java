package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FinanceDtos {
    private FinanceDtos() {}
    public record CreateRequest(@NotNull LocalDate date, @NotNull FinanceBucket bucket, @NotBlank @Size(max = 80) String conceptCode, @NotBlank @Size(max = 80) String categoryCode, @NotNull @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note) {}
    public record PatchRequest(LocalDate date, FinanceBucket bucket, @Size(max = 80) String conceptCode, @Size(max = 80) String categoryCode, @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note) {}
    public record MoneyResponse(BigDecimal ars, BigDecimal usd, BigDecimal exchangeRate) {}
    public record Response(UUID id, LocalDate date, FinanceBucket bucket, MoneyResponse amount, ConfigOptionResponse concept, ConfigOptionResponse category, String note, Instant createdAt, Instant updatedAt) {}
    public record ExchangeRateResponse(String currency, BigDecimal buy, BigDecimal sell, BigDecimal average, Instant fetchedAt, String source) {}
    public record FallbackRequest(@NotNull @DecimalMin("0.00000001") BigDecimal buy, @NotNull @DecimalMin("0.00000001") BigDecimal sell) {}
    public record Summary(LocalDate from, LocalDate to, MoneyResponse income, MoneyResponse expense, MoneyResponse invested, MoneyResponse cash, ExchangeRateResponse exchangeRate) {}
}
