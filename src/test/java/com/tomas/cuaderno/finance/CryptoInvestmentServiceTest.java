package com.tomas.cuaderno.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.errors.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CryptoInvestmentServiceTest {
    @Mock CryptoInvestmentRepository investments;
    @Mock CryptoSaleRepository sales;
    @Mock FinanceAccountRepository accounts;
    @Mock ExchangeRateService rates;

    @Test
    void create_convertsUsdAndPriceIntoSeparatePurchaseLot() {
        UUID owner = UUID.randomUUID();
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("2000000.00")));
        when(investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner)).thenReturn(List.of());
        when(investments.save(any(CryptoInvestment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoDtos.InvestmentResponse response = service().create(owner,
                new CryptoDtos.CreateRequest(LocalDate.of(2026, 9, 3), "btc/usdt", new BigDecimal("500"), new BigDecimal("50"), null, null));

        assertThat(response.assetCode()).isEqualTo("BTCUSDT");
        assertThat(response.amount().usd()).isEqualByComparingTo("500.00000000");
        assertThat(response.unitPriceUsd()).isEqualByComparingTo("50.000000000000");
        assertThat(response.quantity()).isEqualByComparingTo("10.000000000000000000");
    }

    @Test
    void create_whenAvailableBalanceIsInsufficient_rejectsPurchase() {
        UUID owner = UUID.randomUUID();
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("1000.00")));
        when(investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner)).thenReturn(List.of());

        assertThatThrownBy(() -> service().create(owner,
                new CryptoDtos.CreateRequest(LocalDate.now(), "ETHUSDT", new BigDecimal("2"), new BigDecimal("10"), null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Insufficient available balance in crypto account");
    }

    @Test
    void sell_partialLot_recordsUnitPriceAndRealizedProfitAndKeepsProceedsInCrypto() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        FinanceAccount account = account("1000000.00");
        CryptoInvestment purchase = investment(purchaseId, owner, "500", "500000.00", "50", "10");
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(purchase));
        when(sales.findByInvestmentIdAndDeletedAtIsNull(purchaseId)).thenReturn(List.of());
        when(sales.save(any(CryptoSale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoDtos.SaleResponse response = service().sell(owner, purchaseId,
                new CryptoDtos.SellRequest(LocalDate.of(2026, 9, 23), new BigDecimal("4"), new BigDecimal("300"), null));

        assertThat(response.unitPriceUsd()).isEqualByComparingTo("75.000000000000");
        assertThat(response.costBasisUsd()).isEqualByComparingTo("200.00000000");
        assertThat(response.realizedProfitUsd()).isEqualByComparingTo("100.00000000");
        assertThat(purchase.getAmountUsd().subtract(response.costBasisUsd())).isEqualByComparingTo("300.00000000");
        assertThat(account.getBalanceArs()).isEqualByComparingTo("1100000.00");
    }

    @Test
    void sell_entireRemainingLot_releasesAllBasisAndCreditsRealizedGain() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        FinanceAccount account = account("1000000.00");
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(investment(purchaseId, owner, "500", "500000.00", "50", "10")));
        when(sales.findByInvestmentIdAndDeletedAtIsNull(purchaseId)).thenReturn(List.of());
        when(sales.save(any(CryptoSale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CryptoDtos.SaleResponse response = service().sell(owner, purchaseId,
                new CryptoDtos.SellRequest(LocalDate.now(), new BigDecimal("10"), new BigDecimal("700"), null));

        assertThat(response.costBasisUsd()).isEqualByComparingTo("500.00000000");
        assertThat(response.realizedProfitUsd()).isEqualByComparingTo("200.00000000");
        assertThat(account.getBalanceArs()).isEqualByComparingTo("1200000.00");
    }

    @Test
    void sell_whenRequestedQuantityExceedsOpenLot_rejectsSale() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("1000000.00")));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(investment(purchaseId, owner, "500", "500000.00", "50", "10")));
        when(sales.findByInvestmentIdAndDeletedAtIsNull(purchaseId)).thenReturn(List.of());

        assertThatThrownBy(() -> service().sell(owner, purchaseId,
                new CryptoDtos.SellRequest(LocalDate.now(), new BigDecimal("11"), new BigDecimal("700"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("La cantidad supera las unidades disponibles en esta compra.");
    }

    @Test
    void completeLegacyPrice_derivesQuantityFromOriginalUsdAmount() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        CryptoInvestment legacy = investment(purchaseId, owner, "500", "500000.00", null, null);
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("500000.00")));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(legacy));
        when(sales.findByInvestmentIdInOrderByDateDescCreatedAtDesc(List.of(purchaseId))).thenReturn(List.of());

        CryptoDtos.InvestmentResponse response = service().completeLegacyPrice(owner, purchaseId,
                new CryptoDtos.LegacyPriceRequest(new BigDecimal("25")));

        assertThat(response.quantity()).isEqualByComparingTo("20.000000000000000000");
        assertThat(response.remainingQuantity()).isEqualByComparingTo("20.000000000000000000");
        assertThat(legacy.getUnitPriceUsd()).isEqualByComparingTo("25.000000000000");
    }

    @Test
    void voidSale_reversesProfitAndKeepsSaleRecord() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();
        FinanceAccount account = account("1100000.00");
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(investment(purchaseId, owner, "500", "500000.00", "50", "10")));
        CryptoSale sale = new CryptoSale();
        sale.setProceedsUsd(new BigDecimal("300"));
        sale.setExchangeRateSnapshot(new BigDecimal("1000"));
        sale.setCostBasisArs(new BigDecimal("200000.00"));
        sale.setCostBasisUsd(new BigDecimal("200"));
        when(sales.findByIdAndInvestmentIdAndOwnerIdAndDeletedAtIsNull(saleId, purchaseId, owner)).thenReturn(Optional.of(sale));

        service().voidSale(owner, purchaseId, saleId);

        assertThat(account.getBalanceArs()).isEqualByComparingTo("1000000.00");
        assertThat(sale.getDeletedAt()).isNotNull();
        verify(investments).findActiveForUpdate(purchaseId, owner);
    }

    @Test
    void delete_whenPurchaseHasActiveSales_rejectsVoidingLot() {
        UUID owner = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        when(accounts.findActiveForUpdate(owner, "crypto")).thenReturn(Optional.of(account("1000000.00")));
        when(investments.findActiveForUpdate(purchaseId, owner)).thenReturn(Optional.of(investment(purchaseId, owner, "500", "500000.00", "50", "10")));
        when(sales.findByInvestmentIdAndDeletedAtIsNull(purchaseId)).thenReturn(List.of(new CryptoSale()));

        assertThatThrownBy(() -> service().voidPurchase(owner, purchaseId))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Void active sales before voiding this purchase");
    }

    private CryptoInvestmentService service() {
        return new CryptoInvestmentService(investments, sales, accounts, rates);
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
        account.setBalanceUsd(new BigDecimal(balance).divide(new BigDecimal("1000")));
        account.setActive(true);
        return account;
    }

    private CryptoInvestment investment(UUID id, UUID owner, String amountUsd, String amountArs, String unitPrice, String quantity) {
        CryptoInvestment investment = new CryptoInvestment();
        ReflectionTestUtils.setField(investment, "id", id);
        investment.setOwnerId(owner);
        investment.setDate(LocalDate.of(2026, 9, 1));
        investment.setAsset(CryptoAsset.BTCUSDT);
        investment.setAmountUsd(new BigDecimal(amountUsd));
        investment.setAmountArs(new BigDecimal(amountArs));
        investment.setExchangeRateSnapshot(new BigDecimal("1000"));
        if (unitPrice != null) investment.setUnitPriceUsd(new BigDecimal(unitPrice));
        if (quantity != null) investment.setQuantity(new BigDecimal(quantity));
        return investment;
    }
}
