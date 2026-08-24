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
    @Test void analyticsGroupsDailyAndCategoryTotals() {
        UUID owner = UUID.randomUUID(); FinanceService service = new FinanceService(repository, rates, configuration);
        LocalDate from = LocalDate.of(2026, 8, 1); LocalDate to = LocalDate.of(2026, 8, 31);
        FinanceMovementRepository.DailySummaryRow dailyIncome = daily(LocalDate.of(2026, 8, 2), FinanceBucket.INCOME, "100");
        FinanceMovementRepository.DailySummaryRow dailyExpense = daily(LocalDate.of(2026, 8, 2), FinanceBucket.EXPENSE, "30");
        FinanceMovementRepository.CategorySummaryRow incomeCategory = category(FinanceBucket.INCOME, "salary", "100");
        FinanceMovementRepository.CategorySummaryRow expenseCategory = category(FinanceBucket.EXPENSE, "food", "30");
        when(repository.summarizeDaily(owner, from, to)).thenReturn(List.of(dailyIncome, dailyExpense));
        when(repository.summarizeCategories(owner, from, to)).thenReturn(List.of(incomeCategory, expenseCategory));
        FinanceDtos.Analytics result = service.analytics(owner, from, to);
        assertThat(result.daily()).singleElement().satisfies(day -> { assertThat(day.date()).isEqualTo(LocalDate.of(2026, 8, 2)); assertThat(day.income()).isEqualByComparingTo("100.00"); assertThat(day.expense()).isEqualByComparingTo("30.00"); });
        assertThat(result.incomeCategories()).singleElement().satisfies(category -> { assertThat(category.itemCode()).isEqualTo("salary"); assertThat(category.total()).isEqualByComparingTo("100"); });
        assertThat(result.expenseCategories()).singleElement().satisfies(category -> { assertThat(category.itemCode()).isEqualTo("food"); assertThat(category.total()).isEqualByComparingTo("30"); });
    }
    private FinanceMovementRepository.SummaryRow row(FinanceBucket bucket, String amount) { FinanceMovementRepository.SummaryRow row = mock(FinanceMovementRepository.SummaryRow.class); when(row.getBucket()).thenReturn(bucket); when(row.getTotal()).thenReturn(new BigDecimal(amount)); return row; }
    private FinanceMovementRepository.DailySummaryRow daily(LocalDate date, FinanceBucket bucket, String amount) { FinanceMovementRepository.DailySummaryRow row = mock(FinanceMovementRepository.DailySummaryRow.class); when(row.getDate()).thenReturn(date); when(row.getBucket()).thenReturn(bucket); when(row.getTotal()).thenReturn(new BigDecimal(amount)); return row; }
    private FinanceMovementRepository.CategorySummaryRow category(FinanceBucket bucket, String itemCode, String amount) { FinanceMovementRepository.CategorySummaryRow row = mock(FinanceMovementRepository.CategorySummaryRow.class); when(row.getBucket()).thenReturn(bucket); when(row.getItemCode()).thenReturn(itemCode); when(row.getTotal()).thenReturn(new BigDecimal(amount)); return row; }
}
