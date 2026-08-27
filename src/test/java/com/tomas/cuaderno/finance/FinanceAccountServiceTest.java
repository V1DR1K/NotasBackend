package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import com.tomas.cuaderno.common.errors.BadRequestException;
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

    @Test void investmentIncomeMovesMoneyFromCashToInvestment() {
        UUID owner = UUID.randomUUID();
        FinanceAccount cash = account(FinanceAccountGrowthMode.MANUAL, "0"); cash.setCode("mercadopago"); cash.setBalanceArs(new BigDecimal("1000.00"));
        FinanceAccount investment = account(FinanceAccountGrowthMode.MANUAL, "0"); investment.setCode("crypto"); investment.setBalanceArs(new BigDecimal("200.00")); investment.setType(FinanceAccountType.CRYPTO);
        when(repository.findActiveForUpdate(owner, "crypto")).thenReturn(java.util.Optional.of(investment));
        when(repository.findActiveForUpdate(owner, "mercadopago")).thenReturn(java.util.Optional.of(cash));

        new FinanceAccountService(repository).applyMovement(owner, "crypto", FinanceBucket.INCOME, new BigDecimal("250.00"));

        assertThat(cash.getBalanceArs()).isEqualByComparingTo("750.00");
        assertThat(investment.getBalanceArs()).isEqualByComparingTo("450.00");
    }

    @Test void investmentExpenseReturnsMoneyToCash() {
        UUID owner = UUID.randomUUID();
        FinanceAccount cash = account(FinanceAccountGrowthMode.MANUAL, "0"); cash.setCode("mercadopago"); cash.setBalanceArs(new BigDecimal("1000.00"));
        FinanceAccount investment = account(FinanceAccountGrowthMode.MANUAL, "0"); investment.setCode("crypto"); investment.setBalanceArs(new BigDecimal("500.00")); investment.setType(FinanceAccountType.CRYPTO);
        when(repository.findActiveForUpdate(owner, "crypto")).thenReturn(java.util.Optional.of(investment));
        when(repository.findActiveForUpdate(owner, "mercadopago")).thenReturn(java.util.Optional.of(cash));

        new FinanceAccountService(repository).applyMovement(owner, "crypto", FinanceBucket.EXPENSE, new BigDecimal("250.00"));

        assertThat(cash.getBalanceArs()).isEqualByComparingTo("1250.00");
        assertThat(investment.getBalanceArs()).isEqualByComparingTo("250.00");
    }

    @Test void rejectsMovementThatWouldMakeAnAccountNegative() {
        UUID owner = UUID.randomUUID();
        FinanceAccount cash = account(FinanceAccountGrowthMode.MANUAL, "0"); cash.setCode("mercadopago"); cash.setBalanceArs(new BigDecimal("100.00"));
        when(repository.findActiveForUpdate(owner, "mercadopago")).thenReturn(java.util.Optional.of(cash));

        assertThatThrownBy(() -> new FinanceAccountService(repository).applyMovement(owner, "mercadopago", FinanceBucket.EXPENSE, new BigDecimal("101.00")))
                .isInstanceOf(BadRequestException.class);
    }

    private FinanceAccount account(FinanceAccountGrowthMode mode, String rate) {
        FinanceAccount account = new FinanceAccount();
        account.setCode("test"); account.setLabel("Test"); account.setType(FinanceAccountType.CASH); account.setBalanceArs(new BigDecimal("1000.00")); account.setAnnualRatePercent(new BigDecimal(rate)); account.setGrowthMode(mode); account.setBalanceAsOf(Instant.now()); account.setActive(true);
        return account;
    }
}
