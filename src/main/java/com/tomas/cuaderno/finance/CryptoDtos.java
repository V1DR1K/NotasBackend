package com.tomas.cuaderno.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CryptoDtos {
    private CryptoDtos() {}

    public record CreateRequest(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 20) String assetCode,
            @NotNull @DecimalMin(value = "0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal amountUsd,
            @NotNull @DecimalMin(value = "0.000000000001") @Digits(integer = 16, fraction = 12) BigDecimal unitPriceUsd,
            @Size(max = 1000) String note) {}

    public record LegacyPriceRequest(@NotNull @DecimalMin(value = "0.000000000001") @Digits(integer = 16, fraction = 12) BigDecimal unitPriceUsd) {}

    public record SellRequest(
            @NotNull LocalDate date,
            @NotNull @DecimalMin(value = "0.000000000000000001") @Digits(integer = 10, fraction = 18) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00000001") @Digits(integer = 11, fraction = 8) BigDecimal proceedsUsd,
            @Size(max = 1000) String note) {}

    public record MoneyResponse(BigDecimal ars, BigDecimal usd, BigDecimal exchangeRate) {}

    public record InvestmentResponse(
            UUID id,
            LocalDate date,
            String assetCode,
            String assetLabel,
            MoneyResponse amount,
            BigDecimal unitPriceUsd,
            BigDecimal quantity,
            BigDecimal remainingQuantity,
            MoneyResponse remainingCostBasis,
            boolean voided,
            List<SaleResponse> sales,
            String note,
            Instant createdAt) {}

    public record SaleResponse(
            UUID id,
            UUID investmentId,
            LocalDate date,
            BigDecimal quantity,
            BigDecimal proceedsUsd,
            BigDecimal unitPriceUsd,
            BigDecimal costBasisUsd,
            BigDecimal costBasisArs,
            BigDecimal realizedProfitUsd,
            BigDecimal exchangeRate,
            boolean voided,
            String note,
            Instant createdAt) {}

    public record Position(
            String assetCode,
            String assetLabel,
            BigDecimal investedUsd,
            BigDecimal investedArs,
            BigDecimal quantity,
            long purchases) {}

    public record Summary(
            MoneyResponse invested,
            MoneyResponse available,
            BigDecimal realizedProfitUsd,
            List<Position> positions,
            List<InvestmentResponse> investments,
            FinanceDtos.ExchangeRateResponse exchangeRate,
            boolean legacyBalanceEstimated,
            Performance performance) {}

    public record Performance(BigDecimal capitalUsd, BigDecimal purchaseTotalUsd, BigDecimal saleProceedsUsd,
            BigDecimal soldCostBasisUsd, BigDecimal realizedReturnPercent, long salesCount,
            List<ProfitDay> evolution, List<AssetPerformance> assets) {}
    public record ProfitDay(LocalDate date, BigDecimal proceedsUsd, BigDecimal costBasisUsd,
            BigDecimal realizedProfitUsd, BigDecimal cumulativeProfitUsd) {}
    public record AssetPerformance(String assetCode, String assetLabel, BigDecimal realizedProfitUsd,
            BigDecimal proceedsUsd, BigDecimal costBasisUsd) {}
}
