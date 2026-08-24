package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceAccountServiceTest {
    @Mock FinanceAccountRepository repository;

    @Test void projectsDailyTnaBalance() {
        FinanceAccount account = account(FinanceAccountGrowthMode.DAILY_TNA, "18.5");
        account.setBalanceAsOf(Instant.now().minus(1, ChronoUnit.DAYS));
        when(repository.findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(any(UUID.class))).thenReturn(List.of(account));
        FinanceDtos.AccountResponse response = new FinanceAccountService(repository).list(UUID.randomUUID()).getFirst();
        assertThat(response.balanceArs()).isGreaterThan(new BigDecimal("1000.00"));
        assertThat(response.annualRatePercent()).isEqualByComparingTo("18.5");
    }

    @Test void keepsManualBalanceWithoutProjection() {
        FinanceAccount account = account(FinanceAccountGrowthMode.MANUAL, "0");
        account.setBalanceAsOf(Instant.now().minus(10, ChronoUnit.DAYS));
        when(repository.findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(any(UUID.class))).thenReturn(List.of(account));
        FinanceDtos.AccountResponse response = new FinanceAccountService(repository).list(UUID.randomUUID()).getFirst();
        assertThat(response.balanceArs()).isEqualByComparingTo("1000.00");
    }

    private FinanceAccount account(FinanceAccountGrowthMode mode, String rate) {
        FinanceAccount account = new FinanceAccount();
        account.setCode("test"); account.setLabel("Test"); account.setType(FinanceAccountType.CASH); account.setBalanceArs(new BigDecimal("1000.00")); account.setAnnualRatePercent(new BigDecimal(rate)); account.setGrowthMode(mode); account.setBalanceAsOf(Instant.now()); account.setActive(true);
        return account;
    }
}
