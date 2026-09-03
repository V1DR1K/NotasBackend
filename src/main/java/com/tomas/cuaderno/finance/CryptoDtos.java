package com.tomas.cuaderno.finance;

import jakarta.validation.constraints.DecimalMin;
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
            @NotNull @DecimalMin(value = "0.00000001") BigDecimal amountUsd,
            @Size(max = 1000) String note) {}

    public record MoneyResponse(BigDecimal ars, BigDecimal usd, BigDecimal exchangeRate) {}

    public record InvestmentResponse(
            UUID id,
            LocalDate date,
            String assetCode,
            String assetLabel,
            MoneyResponse amount,
            String note,
            Instant createdAt) {}

    public record Position(
            String assetCode,
            String assetLabel,
            BigDecimal investedUsd,
            BigDecimal investedArs,
            long purchases) {}

    public record Summary(
            MoneyResponse invested,
            MoneyResponse available,
            List<Position> positions,
            List<InvestmentResponse> investments,
            FinanceDtos.ExchangeRateResponse exchangeRate) {}
}
