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
@Import({FinanceService.class, FinanceAccountService.class, CryptoInvestmentService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class FinancePersistenceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17").withStartupTimeout(Duration.ofMinutes(3));
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }
    @Autowired FinanceService service;
    @Autowired FinanceAccountService accountService;
    @Autowired CryptoInvestmentService crypto;
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

    @Test void semanticSearchMigrationCreatesVectorIndexAndTracksRecordChanges() {
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname = 'vector'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'ix_semantic_search_documents_embedding' and indexdef ilike '%hnsw%'", Long.class)).isEqualTo(1);

        UUID noteId = UUID.randomUUID();
        jdbc.update("""
                insert into notes(id, owner_id, title, body, category_code, date, project_code)
                values (?, ?, ?, ?, ?, ?, ?)
                """, noteId, owner, "Cocina", "Ideas para una cena liviana", "personal", date, "personal");
        assertThat(jdbc.queryForObject("""
                select content from semantic_search_queue
                where owner_id = ? and source_type = 'notes' and source_id = ?
                """, String.class, owner, noteId)).contains("cena liviana");

        jdbc.update("update notes set body = ?, updated_at = now() where id = ?", "Planificar una caminata", noteId);
        assertThat(jdbc.queryForObject("""
                select content from semantic_search_queue
                where owner_id = ? and source_type = 'notes' and source_id = ?
                """, String.class, owner, noteId)).contains("caminata");

        jdbc.update("update notes set deleted_at = now() where id = ?", noteId);
        assertThat(jdbc.queryForObject("""
                select count(*) from semantic_search_queue
                where owner_id = ? and source_type = 'notes' and source_id = ?
                """, Long.class, owner, noteId)).isZero();
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
        accountService.move(owner, null, "mercadopago", FinanceBucket.EXPENSE, new BigDecimal("105000"), new BigDecimal("1000"));
        var result = service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest(null, null, null, new BigDecimal("12000"), "edited"));
        assertBalance("mercadopago", "7000"); assertBalance("inversiones_pesos", "8000");
        assertThat(result.amount().ars()).isEqualByComparingTo("12000");
        assertThat(result.amount().exchangeRate()).isEqualByComparingTo("1000");
        service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest(null, null, null, null, "note only"));
        assertBalance("mercadopago", "7000"); assertBalance("inversiones_pesos", "8000");
    }

    @Test void editsCanChangeDirectionAndInvestmentAndDeletionReversesBothSides() {
        var movement = transfer("mercadopago", "inversiones_pesos", "30000");
        service.patchTransfer(owner, movement.id(), new FinanceDtos.TransferPatchRequest("mercadopago", "crypto", null, null, null, new BigDecimal("1000")));
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
        var movement = service.transferToCrypto(owner, new FinanceDtos.CryptoTransferRequest(date, new BigDecimal("30000"), null, new BigDecimal("1000")));
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

    @Test void p2pCreditsActualDollarsAndMarketRateChangesDoNotRevalueCash() {
        emptyCryptoWallet();
        var movement = p2p("mercadopago", "crypto", "60000", "1200");
        assertBalance("mercadopago", "40000"); assertBalance("crypto", "60000"); assertUsd("50");
        assertThat(movement.amount().usd()).isEqualByComparingTo("50");
        when(rates.usd(owner)).thenReturn(new FinanceDtos.ExchangeRateResponse("USD", new BigDecimal("2000"), new BigDecimal("2000"), new BigDecimal("2000"), Instant.now(), "test"));
        assertThat(crypto.summary(owner).available().usd()).isEqualByComparingTo("50");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "BTCUSDT", new BigDecimal("30"), new BigDecimal("10"), null));
        assertThat(purchase.amount().ars()).isEqualByComparingTo("36000");
        assertThat(purchase.quantity()).isEqualByComparingTo("3");
        assertThat(crypto.summary(owner).available().usd()).isEqualByComparingTo("20");
        p2p("crypto", "mercadopago", "30000", "1500");
        assertBalance("mercadopago", "70000"); assertBalance("crypto", "36000"); assertUsd("30");
        assertThat(crypto.summary(owner).available().usd()).isZero();
        assertThat(service.summary(owner, date, date).income().ars()).isZero();
        assertThat(service.analytics(owner, date, date).daily()).isEmpty();
    }

    @Test void mixedP2pDepositsUseWeightedCashCostAndEditingRateAppliesNetUsdDifference() {
        emptyCryptoWallet();
        var first = p2p("mercadopago", "crypto", "30000", "1000");
        p2p("mercadopago", "crypto", "30000", "1500");
        assertUsd("50");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "SOLUSDT", new BigDecimal("25"), new BigDecimal("5"), null));
        assertThat(purchase.amount().ars()).isEqualByComparingTo("30000");
        service.patchTransfer(owner, first.id(), new FinanceDtos.TransferPatchRequest(null, null, null, null, null, new BigDecimal("1200")));
        assertUsd("45"); assertBalance("mercadopago", "40000");
        assertThatThrownBy(() -> service.delete(owner, first.id())).isInstanceOf(BadRequestException.class);
        assertUsd("45");
        assertThatThrownBy(() -> p2p("crypto", "mercadopago", "30001", "1500")).isInstanceOf(BadRequestException.class);
        assertBalance("mercadopago", "40000"); assertUsd("45");
    }

    @Test void salesPersistPricesCostAndProfitThenVoidingReversesMetricsAndUsd() {
        emptyCryptoWallet(); p2p("mercadopago", "crypto", "60000", "1200");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "BTCUSDT", new BigDecimal("40"), new BigDecimal("10"), null));
        var sale = crypto.sell(owner, purchase.id(), new CryptoDtos.SellRequest(date.plusDays(1), new BigDecimal("2"), new BigDecimal("30"), null));
        assertThat(sale.unitPriceUsd()).isEqualByComparingTo("15");
        assertThat(sale.costBasisUsd()).isEqualByComparingTo("20");
        assertThat(sale.realizedProfitUsd()).isEqualByComparingTo("10"); assertUsd("60");
        assertThat(jdbc.queryForObject("select unit_price_usd from crypto_sales where id=?", BigDecimal.class, sale.id())).isEqualByComparingTo("15");
        var summary = crypto.summary(owner);
        assertThat(summary.available().usd()).isEqualByComparingTo("40");
        assertThat(summary.performance().capitalUsd()).isEqualByComparingTo("60");
        assertThat(summary.performance().realizedReturnPercent()).isEqualByComparingTo("50");
        assertThat(summary.performance().evolution()).singleElement().satisfies(day -> assertThat(day.cumulativeProfitUsd()).isEqualByComparingTo("10"));
        assertThatThrownBy(() -> crypto.completeLegacyPrice(owner, purchase.id(), new CryptoDtos.LegacyPriceRequest(new BigDecimal("20")))).isInstanceOf(BadRequestException.class);
        crypto.voidSale(owner, purchase.id(), sale.id()); assertUsd("50");
        assertThat(crypto.summary(owner).realizedProfitUsd()).isZero();
        assertThat(crypto.summary(owner).performance().evolution()).isEmpty();
        assertThat(crypto.get(owner, purchase.id()).remainingQuantity()).isEqualByComparingTo("4");
        crypto.voidPurchase(owner, purchase.id());
        assertThat(crypto.summary(owner).available().usd()).isEqualByComparingTo("50");
    }

    @Test void priceCorrectionsArePersistedAndIdempotentAndRecomputeUnitsWithoutChangingCost() {
        emptyCryptoWallet(); p2p("mercadopago", "crypto", "60000", "1200");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "PEPEUSDT", new BigDecimal("40"), new BigDecimal("0.00001"), null));
        var request = new CryptoDtos.LegacyPriceRequest(new BigDecimal("0.00002"));
        crypto.completeLegacyPrice(owner, purchase.id(), request);
        crypto.completeLegacyPrice(owner, purchase.id(), request);
        var persisted = crypto.get(owner, purchase.id());
        assertThat(persisted.unitPriceUsd()).isEqualByComparingTo("0.00002");
        assertThat(persisted.quantity()).isEqualByComparingTo("2000000");
        assertThat(persisted.amount().usd()).isEqualByComparingTo("40"); assertUsd("50");
    }

    @Test void cannotVoidSaleAfterItsProceedsWereSpentAndFailureRollsBackEverything() {
        emptyCryptoWallet(); p2p("mercadopago", "crypto", "60000", "1200");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "ETHUSDT", new BigDecimal("40"), new BigDecimal("10"), null));
        var sale = crypto.sell(owner, purchase.id(), new CryptoDtos.SellRequest(date, new BigDecimal("2"), new BigDecimal("30"), null));
        p2p("crypto", "mercadopago", "48000", "1200");
        assertThatThrownBy(() -> crypto.voidSale(owner, purchase.id(), sale.id())).isInstanceOf(BadRequestException.class);
        assertUsd("20"); assertBalance("mercadopago", "88000");
        assertThat(crypto.get(owner, purchase.id()).sales()).singleElement().satisfies(row -> assertThat(row.voided()).isFalse());
        when(configuration.indexIncludingDeleted(owner, ConfigKind.FINANCE_ITEM)).thenThrow(new IllegalStateException("response failure"));
        assertThatThrownBy(() -> p2p("mercadopago", "crypto", "12000", "1200")).isInstanceOf(IllegalStateException.class);
        assertUsd("20"); assertBalance("mercadopago", "88000");
    }

    @Test void concurrentP2pDepositsPreserveBothUsdAndArs() throws Exception {
        emptyCryptoWallet();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> { try { start.await(); p2p("mercadopago", "crypto", "30000", "1000"); } catch (InterruptedException e) { throw new RuntimeException(e); } });
            Future<?> second = executor.submit(() -> { try { start.await(); p2p("mercadopago", "crypto", "30000", "1500"); } catch (InterruptedException e) { throw new RuntimeException(e); } });
            start.countDown(); first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
            assertUsd("50"); assertBalance("mercadopago", "40000"); assertBalance("crypto", "60000");
        } finally { start.countDown(); }
    }

    @Test void actualP2pRateIsRequiredAndUsdCorrectionsPreserveHistoricalArsCost() {
        emptyCryptoWallet();
        assertThatThrownBy(() -> service.transfer(owner, new FinanceDtos.TransferRequest("mercadopago", "crypto", date, new BigDecimal("1000"), null))).isInstanceOf(BadRequestException.class);
        assertBalance("mercadopago", "100000"); assertUsd("0");
        p2p("mercadopago", "crypto", "60000", "1200");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "BTCUSDT", new BigDecimal("40"), new BigDecimal("10"), null));
        accountService.sync(owner, "crypto", new FinanceDtos.AccountSyncRequest(null, new BigDecimal("48")));
        assertUsd("48"); assertBalance("crypto", "60000"); assertBalance("mercadopago", "40000");
        assertThat(crypto.get(owner, purchase.id()).amount().usd()).isEqualByComparingTo("40");
        assertThat(crypto.summary(owner).available().usd()).isEqualByComparingTo("8");
        assertThat(crypto.summary(owner).legacyBalanceEstimated()).isFalse();
        assertThatThrownBy(() -> accountService.sync(owner, "crypto", new FinanceDtos.AccountSyncRequest(null, new BigDecimal("39")))).isInstanceOf(BadRequestException.class);
        assertUsd("48");
    }

    @Test void completeSaleAtALossConsumesExactResidualBasisAndRecordsNegativeReturn() {
        emptyCryptoWallet(); p2p("mercadopago", "crypto", "60000", "1200");
        var purchase = crypto.create(owner, new CryptoDtos.CreateRequest(date, "SOLUSDT", new BigDecimal("40"), new BigDecimal("3"), null));
        crypto.sell(owner, purchase.id(), new CryptoDtos.SellRequest(date, new BigDecimal("1"), new BigDecimal("2"), null));
        var remaining = crypto.get(owner, purchase.id());
        crypto.sell(owner, purchase.id(), new CryptoDtos.SellRequest(date.plusDays(1), remaining.remainingQuantity(), new BigDecimal("28"), null));
        assertUsd("40");
        var summary = crypto.summary(owner);
        assertThat(summary.invested().usd()).isZero();
        assertThat(summary.available().usd()).isEqualByComparingTo("40");
        assertThat(summary.realizedProfitUsd()).isEqualByComparingTo("-10");
        assertThat(summary.performance().realizedReturnPercent()).isEqualByComparingTo("-25");
        assertThat(summary.performance().soldCostBasisUsd()).isEqualByComparingTo("40");
    }

    void emptyCryptoWallet() { jdbc.update("update finance_accounts set balance_ars=0,balance_usd=0 where owner_id=? and code='crypto'", owner); }
    void assertUsd(String expected) { assertThat(jdbc.queryForObject("select balance_usd from finance_accounts where owner_id=? and code='crypto'", BigDecimal.class, owner)).isEqualByComparingTo(expected); }
    FinanceDtos.Response p2p(String source, String destination, String amount, String rate) {
        return service.transfer(owner, new FinanceDtos.TransferRequest(source, destination, date, new BigDecimal(amount), null, new BigDecimal(rate)));
    }

    FinanceDtos.Response transfer(String source, String destination, String amount) {
        return service.transfer(owner, new FinanceDtos.TransferRequest(source, destination, date, new BigDecimal(amount), null, new BigDecimal("1000")));
    }

    void account(String code, String type, String amount) {
        jdbc.update("insert into finance_accounts(owner_id,code,label,account_type,balance_ars,growth_mode,balance_as_of) values (?,?,?,?,?,'MANUAL',now())", owner, code, code, type, new BigDecimal(amount));
    }
    void assertBalance(String code, String expected) {
        assertThat(jdbc.queryForObject("select balance_ars from finance_accounts where owner_id=? and code=?", BigDecimal.class, owner, code)).isEqualByComparingTo(expected);
    }
}
