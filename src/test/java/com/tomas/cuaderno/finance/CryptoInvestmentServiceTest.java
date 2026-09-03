package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.errors.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoInvestmentServiceTest {
    @Mock CryptoInvestmentRepository investments;
    @Mock FinanceAccountRepository accounts;
    @Mock ExchangeRateService rates;

    @Test
    void create_convertsUsdAndSavesPurchase() {
        UUID owner = UUID.randomUUID();
        FinanceAccount account = account("2000000.00");
        when(rates.usd(owner)).thenReturn(rate("1000"));
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account));
        when(investments.sumAmountArs(owner)).thenReturn(new BigDecimal("0"));
        when(investments.save(any(CryptoInvestment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoDtos.InvestmentResponse response = new CryptoInvestmentService(investments, accounts, rates)
                .create(owner, new CryptoDtos.CreateRequest(LocalDate.of(2026, 9, 3), "btc/usdt", new BigDecimal("1800"), null));

        assertThat(response.assetCode()).isEqualTo("BTCUSDT");
        assertThat(response.amount().usd()).isEqualByComparingTo("1800.00000000");
        assertThat(response.amount().ars()).isEqualByComparingTo("1800000.00");
    }

    @Test
    void create_whenAvailableBalanceIsInsufficient_rejectsPurchase() {
        UUID owner = UUID.randomUUID();
        when(rates.usd(owner)).thenReturn(rate("1000"));
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("1000.00")));
        when(investments.sumAmountArs(owner)).thenReturn(new BigDecimal("500.00"));

        assertThatThrownBy(() -> new CryptoInvestmentService(investments, accounts, rates)
                .create(owner, new CryptoDtos.CreateRequest(LocalDate.now(), "ETHUSDT", new BigDecimal("1"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient available balance in crypto account");
    }

    private FinanceDtos.ExchangeRateResponse rate(String average) {
        BigDecimal value = new BigDecimal(average);
        return new FinanceDtos.ExchangeRateResponse("USD", value, value, value, Instant.now(), "test");
    }

    private FinanceAccount account(String balance) {
        FinanceAccount account = new FinanceAccount();
        account.setCode("crypto");
        account.setLabel("Inversión Cripto");
        account.setType(FinanceAccountType.CRYPTO);
        account.setBalanceArs(new BigDecimal(balance));
        account.setActive(true);
        return account;
    }
}
