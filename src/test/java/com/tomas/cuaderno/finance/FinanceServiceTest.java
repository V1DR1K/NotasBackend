package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.tomas.cuaderno.configuration.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock FinanceMovementRepository repository;
    @Mock ExchangeRateService rates;
    @Mock ConfigurationService configuration;
    @Test void summaryUsesIncomeMinusExpenseMinusInvestedCashRule() {
        UUID owner = UUID.randomUUID(); FinanceService service = new FinanceService(repository, rates, configuration);
        when(rates.usd(owner)).thenReturn(new FinanceDtos.ExchangeRateResponse("USD", new BigDecimal("99"), new BigDecimal("101"), new BigDecimal("100"), java.time.Instant.now(), "test"));
        FinanceMovementRepository.SummaryRow income = row(FinanceBucket.INCOME, "100");
        FinanceMovementRepository.SummaryRow expense = row(FinanceBucket.EXPENSE, "20");
        FinanceMovementRepository.SummaryRow invested = row(FinanceBucket.INVESTED, "30");
        when(repository.summarize(eq(owner), any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(income, expense, invested));
        assertThat(service.summary(owner, LocalDate.now().minusDays(1), LocalDate.now()).cash().ars()).isEqualByComparingTo("50");
    }
    private FinanceMovementRepository.SummaryRow row(FinanceBucket bucket, String amount) { FinanceMovementRepository.SummaryRow row = mock(FinanceMovementRepository.SummaryRow.class); when(row.getBucket()).thenReturn(bucket); when(row.getTotal()).thenReturn(new BigDecimal(amount)); return row; }
}
