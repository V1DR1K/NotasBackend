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
        when(configuration.option(any(), any(), anyString())).thenReturn(new ConfigurationDtos.ConfigOptionResponse("code", "Label", null, 0, true));
        when(repository.findAll(ArgumentMatchers.<Specification<FinanceMovement>>any(), eq(Pageable.unpaged()))).thenReturn(new PageImpl<>(List.of(movement(owner, FinanceBucket.INCOME, "100"), movement(owner, FinanceBucket.EXPENSE, "20"), movement(owner, FinanceBucket.INVESTED, "30"))));
        assertThat(service.summary(owner, LocalDate.now().minusDays(1), LocalDate.now()).cash().ars()).isEqualByComparingTo("50");
    }
    private FinanceMovement movement(UUID owner, FinanceBucket bucket, String amount) { FinanceMovement x = new FinanceMovement(); x.setOwnerId(owner); x.setBucket(bucket); x.setAmountArs(new BigDecimal(amount)); x.setDate(LocalDate.now()); x.setConceptCode("concept"); x.setCategoryCode("category"); x.setExchangeRateSnapshot(new BigDecimal("100")); return x; }
}
