package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.configuration.ConfigurationDtos.ConfigOptionResponse;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {
    private FinanceDtos() {}
    public record CreateRequest(@NotNull LocalDate date, @NotNull FinanceBucket bucket, @NotBlank @Size(max = 80) String itemCode, @NotNull @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note) {}
    public record PatchRequest(LocalDate date, FinanceBucket bucket, @Size(max = 80) String itemCode, @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note) {}
    public record MoneyResponse(BigDecimal ars, BigDecimal usd, BigDecimal exchangeRate) {}
    public record Response(UUID id, LocalDate date, FinanceBucket bucket, MoneyResponse amount, ConfigOptionResponse item, String note, Instant createdAt, Instant updatedAt) {}
    public record ExchangeRateResponse(String currency, BigDecimal buy, BigDecimal sell, BigDecimal average, Instant fetchedAt, String source) {}
    public record FallbackRequest(@NotNull @DecimalMin("0.00000001") BigDecimal buy, @NotNull @DecimalMin("0.00000001") BigDecimal sell) {}
    public record Summary(LocalDate from, LocalDate to, MoneyResponse income, MoneyResponse expense, MoneyResponse invested, MoneyResponse cash, ExchangeRateResponse exchangeRate) {}
    public record Analytics(LocalDate from, LocalDate to, List<DailySummary> daily, List<CategorySummary> incomeCategories, List<CategorySummary> expenseCategories) {}
    public record DailySummary(LocalDate date, BigDecimal income, BigDecimal expense) {}
    public record CategorySummary(String itemCode, BigDecimal total) {}
    public record AccountResponse(String code, String label, FinanceAccountType type, BigDecimal balanceArs, BigDecimal annualRatePercent, FinanceAccountGrowthMode growthMode, Instant balanceAsOf) {}
    public record AccountSyncRequest(@NotNull @DecimalMin(value = "0.00") BigDecimal balanceArs) {}
}
