package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tomas.cuaderno.configuration.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.*;
import com.tomas.cuaderno.common.errors.BadRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FinanceService.class, FinanceAccountService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class FinancePersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired FinanceService service;
    @Autowired FinanceAccountService accountService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ConfigurationService configuration;
    @MockitoBean ExchangeRateService rates;
    UUID owner;
    final LocalDate date = LocalDate.of(2026, 9, 30);

    @BeforeEach void setup() {
        owner = UUID.randomUUID();
        jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,?)", owner, owner.toString(), "unused", "USER");
        account("mercadopago", "CASH", "100000");
        account("inversiones_pesos", "INVESTMENT", "20000");
        account("crypto", "CRYPTO", "20000");
        when(rates.average(owner)).thenReturn(new BigDecimal("1000"));
        when(rates.usd(owner)).thenReturn(new FinanceDtos.ExchangeRateResponse("USD", new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("1000"), Instant.now(), "test"));
        Map<String, ConfigurationDtos.ConfigOptionResponse> options = new HashMap<>();
        for (var entry : Map.of("transferencia", FinanceItemType.TRANSFER, "sueldo", FinanceItemType.INCOME, "supermercado", FinanceItemType.EXPENSE).entrySet()) {
            ConfigItem item = new ConfigItem(); item.setCode(entry.getKey()); item.setFinanceType(entry.getValue());
            when(configuration.requireActive(eq(owner), eq(ConfigKind.FINANCE_ITEM), eq(entry.getKey()), anyString())).thenReturn(item);
            options.put(entry.getKey(), new ConfigurationDtos.ConfigOptionResponse(entry.getKey(), entry.getKey(), null, 0, true, entry.getValue()));
        }
        when(configuration.indexIncludingDeleted(owner, ConfigKind.FINANCE_ITEM)).thenReturn(options);
    }

    @Test void legacyPesoInvestmentIncomePersistsBothBalances() {
        service.create(owner, new FinanceDtos.CreateRequest(date, FinanceBucket.INCOME, "inversiones_pesos", "transferencia", new BigDecimal("30000"), null));
        assertBalance("mercadopago", "70000");
        assertBalance("inversiones_pesos", "50000");
        assertThat(jdbc.queryForObject("select count(*) from finance_movements where owner_id=? and balance_applied=true", Long.class, owner)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"inversiones_pesos", "crypto"})
    void transfersInBothDirectionsPersistAndKeepExternalTotalsUnchanged(String investment) {
        var created = transfer("mercadopago", investment, "30000");
        assertThat(created.movementType()).isEqualTo(FinanceMovementType.TRANSFER);
        assertThat(created.sourceAccountCode()).isEqualTo("mercadopago");
        assertThat(created.destinationAccountCode()).isEqualTo(investment);
        assertBalance("mercadopago", "70000"); assertBalance(investment, "50000");
        transfer(investment, "mercadopago", "10000");
        assertBalance("mercadopago", "80000"); assertBalance(investment, "40000");
        assertThat(service.summary(owner, date, date).income().ars()).isZero();
        assertThat(service.summary(owner, date, date).expense().ars()).isZero();
        assertThat(service.analytics(owner, date, date).daily()).isEmpty();
        assertThat(service.analytics(owner, date, date).incomeCategories()).isEmpty();
        assertThat(service.analytics(owner, date, date).expenseCategories()).isEmpty();
        assertThat(service.list(owner, null, null, null, null, null, null, null, PageRequest.of(0, 20), FinanceMovementType.TRANSFER).totalElements()).isEqualTo(2);
        assertThat(service.list(owner, null, null, null, null, null, null, null, PageRequest.of(0, 20), FinanceMovementType.INCOME).content()).isEmpty();
        assertThat(service.list(owner, null, null, null, null, null, null, null, PageRequest.of(0, 20), FinanceMovementType.EXPENSE).content()).isEmpty();
    }

    @Test void externalIncomeAndExpenseAreTheOnlyAnalyticsEntries() {
        service.create(owner, new FinanceDtos.CreateRequest(date, FinanceBucket.INCOME, "mercadopago", "sueldo", new BigDecimal("2000"), null));
        service.create(owner, new FinanceDtos.CreateRequest(date, FinanceBucket.EXPENSE, "mercadopago", "supermercado", new BigDecimal("500"), null));
        transfer("mercadopago", "inversiones_pesos", "30000");
        transfer("crypto", "mercadopago", "10000");
        var summary = service.summary(owner, date, date);
        var analytics = service.analytics(owner, date, date);
        assertThat(summary.income().ars()).isEqualByComparingTo("2000");
        assertThat(summary.expense().ars()).isEqualByComparingTo("500");
        assertThat(analytics.daily()).singleElement().satisfies(day -> {
            assertThat(day.income()).isEqualByComparingTo("2000"); assertThat(day.expense()).isEqualByComparingTo("500");
        });
        assertThat(analytics.incomeCategories()).singleElement().extracting(FinanceDtos.CategorySummary::itemCode).isEqualTo("sueldo");
        assertThat(analytics.expenseCategories()).singleElement().extracting(FinanceDtos.CategorySummary::itemCode).isEqualTo("supermercado");
    }

    @Test void editsApplyOnlyTheNetDifferenceEvenAfterMoneyHasBeenSpent() {
        var movement = transfer("inversiones_pesos", "mercadopago", "10000");
        // Spend the returned funds and most of the original wallet balance.
        accountService.applyMovement(owner, "mercadopago", FinanceBucket.EXPENSE, new BigDecimal("105000"));
        var result = service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest(null, null, null, new BigDecimal("12000"), "edited"));
        assertBalance("mercadopago", "7000"); assertBalance("inversiones_pesos", "8000");
        assertThat(result.amount().ars()).isEqualByComparingTo("12000");
        assertThat(result.amount().exchangeRate()).isEqualByComparingTo("1000");
        service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest(null, null, null, null, "note only"));
        assertBalance("mercadopago", "7000"); assertBalance("inversiones_pesos", "8000");
    }

    @Test void editsCanChangeDirectionAndInvestmentAndDeletionReversesBothSides() {
        var movement = transfer("mercadopago", "inversiones_pesos", "30000");
        service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest("mercadopago", "crypto", null, null, null));
        assertBalance("mercadopago", "70000"); assertBalance("inversiones_pesos", "20000"); assertBalance("crypto", "50000");
        service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest("crypto", "mercadopago", null, new BigDecimal("10000"), null));
        assertBalance("mercadopago", "110000"); assertBalance("crypto", "10000");
        service.delete(owner, movement.id());
        assertBalance("mercadopago", "100000"); assertBalance("crypto", "20000");
        assertThat(jdbc.queryForObject("select count(*) from finance_movements where owner_id=? and deleted_at is null", Long.class, owner)).isZero();
    }

    @Test void insufficientBalanceAndForbiddenRoutesLeaveAllBalancesUnchanged() {
        assertThatThrownBy(() -> transfer("mercadopago", "inversiones_pesos", "100001")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transfer("inversiones_pesos", "mercadopago", "20001")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transfer("inversiones_pesos", "crypto", "1")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transfer("mercadopago", "mercadopago", "1")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> transfer("mercadopago", "crypto", "0")).isInstanceOf(BadRequestException.class);
        assertBalance("mercadopago", "100000"); assertBalance("inversiones_pesos", "20000"); assertBalance("crypto", "20000");
        assertThat(jdbc.queryForObject("select count(*) from finance_movements where owner_id=?", Long.class, owner)).isZero();
    }

    @Test void inactiveAndOtherOwnersAccountsCannotBeUsed() {
        jdbc.update("update finance_accounts set active=false where owner_id=? and code='crypto'", owner);
        assertThatThrownBy(() -> transfer("mercadopago", "crypto", "1")).isInstanceOf(com.tomas.cuaderno.common.errors.NotFoundException.class);
        UUID otherOwner = UUID.randomUUID();
        jdbc.update("insert into app_users(id,username,password_hash,role) values (?,?,?,?)", otherOwner, otherOwner.toString(), "unused", "USER");
        jdbc.update("update finance_accounts set owner_id=? where owner_id=? and code='inversiones_pesos'", otherOwner, owner);
        assertThatThrownBy(() -> transfer("mercadopago", "inversiones_pesos", "1")).isInstanceOf(com.tomas.cuaderno.common.errors.NotFoundException.class);
        assertBalance("mercadopago", "100000");
    }

    @Test void cryptoWithdrawalsAndCorrectionsCannotConsumeOpenPositions() {
        UUID investment = UUID.randomUUID();
        jdbc.update("insert into crypto_investments(id,owner_id,date,asset_code,amount_usd,amount_ars,exchange_rate_snapshot) values (?,?,?,'BTCUSDT',15,15000,1000)", investment, owner, date);
        assertThatThrownBy(() -> transfer("crypto", "mercadopago", "6000")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> accountService.sync(owner, "crypto", new FinanceDtos.AccountSyncRequest(new BigDecimal("14999")))).isInstanceOf(BadRequestException.class);
        assertBalance("mercadopago", "100000"); assertBalance("crypto", "20000");
        jdbc.update("insert into crypto_sales(owner_id,investment_id,date,quantity,proceeds_usd,unit_price_usd,cost_basis_usd,cost_basis_ars,exchange_rate_snapshot) values (?,?,?,1,5,5,5,5000,1000)", owner, investment, date);
        transfer("crypto", "mercadopago", "10000");
        assertBalance("mercadopago", "110000"); assertBalance("crypto", "10000");
    }

    @Test void failureAfterSavingMovementRollsBackBothAccountsAndRecord() {
        when(configuration.indexIncludingDeleted(owner, ConfigKind.FINANCE_ITEM)).thenThrow(new IllegalStateException("response failure"));
        assertThatThrownBy(() -> transfer("mercadopago", "inversiones_pesos", "30000")).isInstanceOf(IllegalStateException.class);
        assertBalance("mercadopago", "100000"); assertBalance("inversiones_pesos", "20000");
        assertThat(jdbc.queryForObject("select count(*) from finance_movements where owner_id=?", Long.class, owner)).isZero();
    }

    @Test void failedEditAndDeletionPreserveExistingRecordAndBalances() {
        var movement = transfer("mercadopago", "crypto", "30000");
        assertThatThrownBy(() -> service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest(null, null, null, new BigDecimal("100001"), null))).isInstanceOf(BadRequestException.class);
        assertThat(service.get(owner, movement.id()).amount().ars()).isEqualByComparingTo("30000");
        jdbc.update("insert into crypto_investments(owner_id,date,asset_code,amount_usd,amount_ars,exchange_rate_snapshot) values (?,?,'BTCUSDT',40,40000,1000)", owner, date);
        assertThatThrownBy(() -> service.delete(owner, movement.id())).isInstanceOf(BadRequestException.class);
        assertBalance("mercadopago", "70000"); assertBalance("crypto", "50000");
        assertThat(service.get(owner, movement.id()).movementType()).isEqualTo(FinanceMovementType.TRANSFER);
    }

    @Test void oldCryptoTransferEndpointUsesTheSameAccounting() {
        var movement = service.transferToCrypto(owner, new FinanceDtos.CryptoTransferRequest(date, new BigDecimal("30000"), null));
        service.patch(owner, movement.id(), new FinanceDtos.PatchRequest(null, null, null, null, new BigDecimal("20000"), null));
        assertBalance("mercadopago", "80000"); assertBalance("crypto", "40000");
        service.delete(owner, movement.id());
        assertBalance("mercadopago", "100000"); assertBalance("crypto", "20000");
    }

    @ParameterizedTest @ValueSource(strings = {"30000", "60000"})
    void concurrentTransfersSerializeAndNeverLoseMoney(String amount) throws Exception {
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                ready.countDown(); start.await();
                try { transfer("mercadopago", "inversiones_pesos", amount); return true; }
                catch (BadRequestException insufficientBalance) { return false; }
            };
            Future<Boolean> first = executor.submit(task), second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            int successful = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successful).isEqualTo(amount.equals("30000") ? 2 : 1);
            BigDecimal moved = new BigDecimal(amount).multiply(BigDecimal.valueOf(successful));
            assertBalance("mercadopago", new BigDecimal("100000").subtract(moved).toPlainString());
            assertBalance("inversiones_pesos", new BigDecimal("20000").add(moved).toPlainString());
            assertThat(jdbc.queryForObject("select count(*) from finance_movements where owner_id=?", Long.class, owner)).isEqualTo(successful);
        } finally { start.countDown(); }
    }

    FinanceDtos.Response transfer(String source, String destination, String amount) {
        return service.transfer(owner, new FinanceDtos.TransferRequest(source, destination, date, new BigDecimal(amount), null));
    }

    void account(String code, String type, String amount) {
        jdbc.update("insert into finance_accounts(owner_id,code,label,account_type,balance_ars,growth_mode,balance_as_of) values (?,?,?,?,?,'MANUAL',now())", owner, code, code, type, new BigDecimal(amount));
    }
    void assertBalance(String code, String expected) {
        assertThat(jdbc.queryForObject("select balance_ars from finance_accounts where owner_id=? and code=?", BigDecimal.class, owner, code)).isEqualByComparingTo(expected);
    }
}
