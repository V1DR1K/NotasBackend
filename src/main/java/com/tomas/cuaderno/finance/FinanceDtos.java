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
    public record CreateRequest(@NotNull LocalDate date, @NotNull FinanceBucket bucket, @NotBlank @Size(max = 80) String accountCode, @NotBlank @Size(max = 80) String itemCode, @NotNull @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note, @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate) { public CreateRequest(LocalDate date, FinanceBucket bucket, String accountCode, String itemCode, BigDecimal amountArs, String note) { this(date, bucket, accountCode, itemCode, amountArs, note, null); } }
    public record PatchRequest(LocalDate date, FinanceBucket bucket, @Size(max = 80) String accountCode, @Size(max = 80) String itemCode, @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note, @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate) { public PatchRequest(LocalDate date, FinanceBucket bucket, String accountCode, String itemCode, BigDecimal amountArs, String note) { this(date, bucket, accountCode, itemCode, amountArs, note, null); } }
    public record MoneyResponse(BigDecimal ars, BigDecimal usd, BigDecimal exchangeRate) {}
    public record Response(UUID id, LocalDate date, FinanceBucket bucket, String accountCode, MoneyResponse amount, ConfigOptionResponse item, String note, Instant createdAt, Instant updatedAt, FinanceMovementType movementType, String sourceAccountCode, String destinationAccountCode) {}
    public record TransferRequest(@NotBlank @Size(max = 80) String sourceAccountCode, @NotBlank @Size(max = 80) String destinationAccountCode, @NotNull LocalDate date, @NotNull @DecimalMin("0.01") BigDecimal amountArs, @Size(max = 1000) String note, @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate) { public TransferRequest(String sourceAccountCode, String destinationAccountCode, LocalDate date, BigDecimal amountArs, String note) { this(sourceAccountCode, destinationAccountCode, date, amountArs, note, null); } }
    public record TransferPatchRequest(@Size(max = 80) String sourceAccountCode, @Size(max = 80) String destinationAccountCode, LocalDate date, @DecimalMin("0.01") BigDecimal amountArs, @Size(max = 1000) String note, @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate) { public TransferPatchRequest(String sourceAccountCode, String destinationAccountCode, LocalDate date, BigDecimal amountArs, String note) { this(sourceAccountCode, destinationAccountCode, date, amountArs, note, null); } }
    public record ExchangeRateResponse(String currency, BigDecimal buy, BigDecimal sell, BigDecimal average, Instant fetchedAt, String source) {}
    public record FallbackRequest(@NotNull @DecimalMin("0.00000001") BigDecimal buy, @NotNull @DecimalMin("0.00000001") BigDecimal sell) {}
    public record Summary(LocalDate from, LocalDate to, MoneyResponse income, MoneyResponse expense, MoneyResponse invested, MoneyResponse cash, ExchangeRateResponse exchangeRate) {}
    public record Analytics(LocalDate from, LocalDate to, List<DailySummary> daily, List<CategorySummary> incomeCategories, List<CategorySummary> expenseCategories) {}
    public record DailySummary(LocalDate date, BigDecimal income, BigDecimal expense) {}
    public record CategorySummary(String itemCode, BigDecimal total) {}
    public record AccountResponse(String code, String label, FinanceAccountType type, BigDecimal balanceArs, BigDecimal annualRatePercent, FinanceAccountGrowthMode growthMode, Instant balanceAsOf, BigDecimal balanceUsd, boolean usdBalanceEstimated) {}
    public record AccountSyncRequest(@DecimalMin("0.00") BigDecimal balanceArs, @DecimalMin("0.00") @Digits(integer = 11, fraction = 8) BigDecimal balanceUsd) { public AccountSyncRequest(BigDecimal balanceArs) { this(balanceArs, null); } }
    public record CryptoTransferRequest(@NotNull LocalDate date, @NotNull @DecimalMin(value = "0.01") BigDecimal amountArs, @Size(max = 1000) String note, @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal exchangeRate) { public CryptoTransferRequest(LocalDate date, BigDecimal amountArs, String note) { this(date, amountArs, note, null); } }
}
