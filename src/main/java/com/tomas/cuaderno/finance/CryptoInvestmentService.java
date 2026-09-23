package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CryptoInvestmentService {
    private static final String CRYPTO_ACCOUNT = "crypto";
    private static final int USD_SCALE = 8;
    private static final int ARS_SCALE = 2;
    private static final int PRICE_SCALE = 12;
    private static final int QUANTITY_SCALE = 18;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final CryptoInvestmentRepository investments;
    private final CryptoSaleRepository sales;
    private final FinanceAccountRepository accounts;
    private final ExchangeRateService rates;

    public CryptoInvestmentService(CryptoInvestmentRepository investments, CryptoSaleRepository sales,
            FinanceAccountRepository accounts, ExchangeRateService rates) {
        this.investments = investments;
        this.sales = sales;
        this.accounts = accounts;
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public List<CryptoDtos.InvestmentResponse> list(UUID owner) {
        List<CryptoInvestment> rows = investments.findByOwnerIdOrderByDateDescCreatedAtDesc(owner);
        Map<UUID, List<CryptoSale>> salesByInvestment = salesFor(rows);
        return rows.stream().map(item -> response(item, salesByInvestment.getOrDefault(item.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public CryptoDtos.Summary summary(UUID owner) {
        FinanceDtos.ExchangeRateResponse rate = rates.usd(owner);
        List<CryptoInvestment> activeInvestments = investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner);
        Map<UUID, List<CryptoSale>> salesByInvestment = salesFor(activeInvestments);
        BigDecimal investedArs = BigDecimal.ZERO;
        BigDecimal investedUsd = BigDecimal.ZERO;
        BigDecimal realizedProfitUsd = BigDecimal.ZERO;
        Map<CryptoAsset, PositionTotals> byAsset = new HashMap<>();
        for (CryptoInvestment item : activeInvestments) {
            List<CryptoSale> itemSales = salesByInvestment.getOrDefault(item.getId(), List.of());
            BigDecimal remainingArs = remainingBasisArs(item, itemSales);
            BigDecimal remainingUsd = remainingBasisUsd(item, itemSales);
            investedArs = investedArs.add(remainingArs);
            investedUsd = investedUsd.add(remainingUsd);
            realizedProfitUsd = realizedProfitUsd.add(activeProfitUsd(itemSales));
            PositionTotals totals = byAsset.computeIfAbsent(item.getAsset(), ignored -> new PositionTotals());
            totals.investedArs = totals.investedArs.add(remainingArs);
            totals.investedUsd = totals.investedUsd.add(remainingUsd);
            if (remainingUsd.signum() > 0) totals.purchases++;
            if (item.getQuantity() == null || item.getUnitPriceUsd() == null) {
                totals.quantityKnown = false;
            } else {
                totals.quantity = totals.quantity.add(remainingQuantity(item, itemSales));
            }
        }

        BigDecimal accountBalance = cryptoAccount(owner, false).getBalanceArs();
        BigDecimal availableArs = accountBalance.subtract(money(investedArs)).max(BigDecimal.ZERO).setScale(ARS_SCALE, ROUNDING);
        List<CryptoDtos.Position> positions = byAsset.entrySet().stream()
                .filter(entry -> entry.getValue().investedUsd.signum() > 0)
                .sorted((left, right) -> right.getValue().investedUsd.compareTo(left.getValue().investedUsd))
                .map(entry -> new CryptoDtos.Position(entry.getKey().name(), entry.getKey().label(),
                        usd(entry.getValue().investedUsd), money(entry.getValue().investedArs),
                        entry.getValue().quantityKnown ? quantity(entry.getValue().quantity) : null,
                        entry.getValue().purchases))
                .toList();
        return new CryptoDtos.Summary(
                new CryptoDtos.MoneyResponse(money(investedArs), usd(investedUsd), rate.average()),
                new CryptoDtos.MoneyResponse(availableArs, usd(availableArs.divide(rate.average(), USD_SCALE, ROUNDING)), rate.average()),
                usd(realizedProfitUsd),
                positions,
                list(owner),
                rate);
    }

    @Transactional
    public CryptoDtos.InvestmentResponse create(UUID owner, CryptoDtos.CreateRequest request) {
        CryptoAsset asset = CryptoAsset.parse(request.assetCode());
        FinanceAccount account = cryptoAccount(owner, true);
        FinanceDtos.ExchangeRateResponse rate = rates.usd(owner);
        BigDecimal amountUsd = usd(request.amountUsd());
        BigDecimal unitPriceUsd = price(request.unitPriceUsd());
        BigDecimal units = amountUsd.divide(unitPriceUsd, QUANTITY_SCALE, ROUNDING);
        if (units.signum() <= 0 || integerDigits(units) > 10) throw new BadRequestException("Purchase amount and price produce an unsupported crypto quantity");
        BigDecimal amountArs = money(amountUsd.multiply(rate.average()));
        BigDecimal availableArs = account.getBalanceArs().subtract(openBasisArs(owner)).setScale(ARS_SCALE, ROUNDING);
        if (availableArs.signum() < 0 || amountArs.compareTo(availableArs) > 0) {
            throw new BadRequestException("Insufficient available balance in crypto account");
        }
        CryptoInvestment investment = new CryptoInvestment();
        investment.setOwnerId(owner);
        investment.setDate(request.date());
        investment.setAsset(asset);
        investment.setAmountUsd(amountUsd);
        investment.setAmountArs(amountArs);
        investment.setExchangeRateSnapshot(rate.average());
        investment.setUnitPriceUsd(unitPriceUsd);
        investment.setQuantity(units);
        investment.setNote(request.note());
        CryptoInvestment saved = investments.save(investment);
        return response(saved, List.of());
    }

    @Transactional
    public CryptoDtos.InvestmentResponse completeLegacyPrice(UUID owner, UUID id, CryptoDtos.LegacyPriceRequest request) {
        cryptoAccount(owner, true);
        CryptoInvestment investment = investments.findActiveForUpdate(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        if (investment.getUnitPriceUsd() != null || investment.getQuantity() != null) {
            throw new BadRequestException("Purchase price is already recorded");
        }
        BigDecimal unitPriceUsd = price(request.unitPriceUsd());
        BigDecimal quantity = investment.getAmountUsd().divide(unitPriceUsd, QUANTITY_SCALE, ROUNDING);
        if (quantity.signum() <= 0 || integerDigits(quantity) > 10) throw new BadRequestException("Purchase amount and price produce an unsupported crypto quantity");
        investment.setUnitPriceUsd(unitPriceUsd);
        investment.setQuantity(quantity);
        return response(investment, sales.findByInvestmentIdInOrderByDateDescCreatedAtDesc(List.of(id)));
    }

    @Transactional
    public CryptoDtos.SaleResponse sell(UUID owner, UUID id, CryptoDtos.SellRequest request) {
        FinanceAccount account = cryptoAccount(owner, true);
        CryptoInvestment investment = investments.findActiveForUpdate(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        if (investment.getQuantity() == null || investment.getUnitPriceUsd() == null) {
            throw new BadRequestException("Complete the purchase unit price before selling this lot");
        }
        List<CryptoSale> activeSales = sales.findByInvestmentIdAndDeletedAtIsNull(id);
        BigDecimal remainingQuantity = remainingQuantity(investment, activeSales);
        BigDecimal soldQuantity = quantity(request.quantity());
        if (soldQuantity.signum() <= 0 || soldQuantity.compareTo(remainingQuantity) > 0) {
            throw new BadRequestException("Sale quantity exceeds the remaining quantity in this purchase");
        }
        BigDecimal proceedsUsd = usd(request.proceedsUsd());
        BigDecimal remainingUsd = remainingBasisUsd(investment, activeSales);
        BigDecimal remainingArs = remainingBasisArs(investment, activeSales);
        boolean closesLot = soldQuantity.compareTo(remainingQuantity) == 0;
        BigDecimal costBasisUsd = closesLot ? remainingUsd
                : usd(remainingUsd.multiply(soldQuantity).divide(remainingQuantity, 18, ROUNDING));
        BigDecimal costBasisArs = closesLot ? remainingArs
                : money(remainingArs.multiply(soldQuantity).divide(remainingQuantity, 18, ROUNDING));
        if (costBasisUsd.signum() <= 0) throw new BadRequestException("Sale quantity is below the supported precision");

        FinanceDtos.ExchangeRateResponse rate = rates.usd(owner);
        BigDecimal saleUnitPrice = proceedsUsd.divide(soldQuantity, PRICE_SCALE, ROUNDING);
        BigDecimal realizedProfitArs = money(proceedsUsd.multiply(rate.average())).subtract(costBasisArs);
        BigDecimal nextAccountBalance = money(account.getBalanceArs().add(realizedProfitArs));
        if (nextAccountBalance.signum() < 0) throw new BadRequestException("Sale would make the crypto account balance negative");
        account.setBalanceArs(nextAccountBalance);
        account.setBalanceAsOf(Instant.now());

        CryptoSale sale = new CryptoSale();
        sale.setOwnerId(owner);
        sale.setInvestmentId(id);
        sale.setDate(request.date());
        sale.setQuantity(soldQuantity);
        sale.setProceedsUsd(proceedsUsd);
        sale.setUnitPriceUsd(saleUnitPrice);
        sale.setCostBasisUsd(costBasisUsd);
        sale.setCostBasisArs(costBasisArs);
        sale.setExchangeRateSnapshot(rate.average());
        sale.setNote(request.note());
        return saleResponse(sales.save(sale));
    }

    @Transactional
    public void voidSale(UUID owner, UUID investmentId, UUID saleId) {
        FinanceAccount account = cryptoAccount(owner, true);
        investments.findActiveForUpdate(investmentId, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        CryptoSale sale = sales.findByIdAndInvestmentIdAndOwnerIdAndDeletedAtIsNull(saleId, investmentId, owner)
                .orElseThrow(() -> new NotFoundException("Crypto sale not found"));
        BigDecimal realizedProfitArs = money(sale.getProceedsUsd().multiply(sale.getExchangeRateSnapshot())).subtract(sale.getCostBasisArs());
        BigDecimal nextBalance = money(account.getBalanceArs().subtract(realizedProfitArs));
        if (nextBalance.signum() < 0) throw new BadRequestException("Voiding this sale would make the crypto account balance negative");
        account.setBalanceArs(nextBalance);
        account.setBalanceAsOf(Instant.now());
        sale.setDeletedAt(Instant.now());
    }

    @Transactional
    public void voidPurchase(UUID owner, UUID id) {
        cryptoAccount(owner, true);
        CryptoInvestment investment = investments.findActiveForUpdate(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        if (!sales.findByInvestmentIdAndDeletedAtIsNull(id).isEmpty()) {
            throw new BadRequestException("Void active sales before voiding this purchase");
        }
        investment.setDeletedAt(Instant.now());
    }

    private FinanceAccount cryptoAccount(UUID owner, boolean lock) {
        return (lock
                ? accounts.findActiveForUpdate(owner, CRYPTO_ACCOUNT)
                : accounts.findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(owner, CRYPTO_ACCOUNT).filter(FinanceAccount::isActive))
                .orElseThrow(() -> new NotFoundException("Crypto account not found"));
    }

    private BigDecimal openBasisArs(UUID owner) {
        List<CryptoInvestment> rows = investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner);
        Map<UUID, List<CryptoSale>> byInvestment = salesFor(rows);
        return rows.stream().map(item -> remainingBasisArs(item, byInvestment.getOrDefault(item.getId(), List.of())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(ARS_SCALE, ROUNDING);
    }

    private Map<UUID, List<CryptoSale>> salesFor(List<CryptoInvestment> rows) {
        if (rows.isEmpty()) return Map.of();
        List<UUID> ids = rows.stream().map(CryptoInvestment::getId).toList();
        return sales.findByInvestmentIdInOrderByDateDescCreatedAtDesc(ids).stream()
                .collect(Collectors.groupingBy(CryptoSale::getInvestmentId));
    }

    private CryptoDtos.InvestmentResponse response(CryptoInvestment investment, List<CryptoSale> itemSales) {
        BigDecimal remainingQty = investment.getQuantity() == null || investment.getDeletedAt() != null
                ? null : remainingQuantity(investment, itemSales.stream().filter(sale -> sale.getDeletedAt() == null).toList());
        return new CryptoDtos.InvestmentResponse(
                investment.getId(), investment.getDate(), investment.getAsset().name(), investment.getAsset().label(),
                new CryptoDtos.MoneyResponse(investment.getAmountArs(), investment.getAmountUsd(), investment.getExchangeRateSnapshot()),
                investment.getUnitPriceUsd(), investment.getQuantity(), remainingQty,
                new CryptoDtos.MoneyResponse(
                        investment.getDeletedAt() == null ? remainingBasisArs(investment, itemSales) : BigDecimal.ZERO.setScale(ARS_SCALE),
                        investment.getDeletedAt() == null ? remainingBasisUsd(investment, itemSales) : BigDecimal.ZERO.setScale(USD_SCALE),
                        investment.getExchangeRateSnapshot()),
                investment.getDeletedAt() != null,
                itemSales.stream().map(this::saleResponse).toList(), investment.getNote(), investment.getCreatedAt());
    }

    private CryptoDtos.SaleResponse saleResponse(CryptoSale sale) {
        return new CryptoDtos.SaleResponse(sale.getId(), sale.getInvestmentId(), sale.getDate(), sale.getQuantity(),
                sale.getProceedsUsd(), sale.getUnitPriceUsd(), sale.getCostBasisUsd(), sale.getCostBasisArs(),
                usd(sale.getProceedsUsd().subtract(sale.getCostBasisUsd())), sale.getExchangeRateSnapshot(),
                sale.getDeletedAt() != null, sale.getNote(), sale.getCreatedAt());
    }

    private BigDecimal remainingQuantity(CryptoInvestment investment, List<CryptoSale> itemSales) {
        if (investment.getQuantity() == null) return BigDecimal.ZERO.setScale(QUANTITY_SCALE);
        BigDecimal sold = itemSales.stream().filter(sale -> sale.getDeletedAt() == null)
                .map(CryptoSale::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        return investment.getQuantity().subtract(sold).max(BigDecimal.ZERO).setScale(QUANTITY_SCALE, ROUNDING);
    }

    private BigDecimal remainingBasisUsd(CryptoInvestment investment, List<CryptoSale> itemSales) {
        BigDecimal soldBasis = itemSales.stream().filter(sale -> sale.getDeletedAt() == null)
                .map(CryptoSale::getCostBasisUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
        return investment.getAmountUsd().subtract(soldBasis).max(BigDecimal.ZERO).setScale(USD_SCALE, ROUNDING);
    }

    private BigDecimal remainingBasisArs(CryptoInvestment investment, List<CryptoSale> itemSales) {
        BigDecimal soldBasis = itemSales.stream().filter(sale -> sale.getDeletedAt() == null)
                .map(CryptoSale::getCostBasisArs).reduce(BigDecimal.ZERO, BigDecimal::add);
        return investment.getAmountArs().subtract(soldBasis).max(BigDecimal.ZERO).setScale(ARS_SCALE, ROUNDING);
    }

    private BigDecimal activeProfitUsd(List<CryptoSale> itemSales) {
        return itemSales.stream().filter(sale -> sale.getDeletedAt() == null)
                .map(sale -> sale.getProceedsUsd().subtract(sale.getCostBasisUsd()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal price(BigDecimal value) { return value.setScale(PRICE_SCALE, ROUNDING); }
    private BigDecimal quantity(BigDecimal value) { return value.setScale(QUANTITY_SCALE, ROUNDING); }
    private int integerDigits(BigDecimal value) { return Math.max(0, value.precision() - value.scale()); }
    private BigDecimal money(BigDecimal value) { return value.setScale(ARS_SCALE, ROUNDING); }
    private BigDecimal usd(BigDecimal value) { return value.setScale(USD_SCALE, ROUNDING); }

    private static final class PositionTotals {
        private BigDecimal investedUsd = BigDecimal.ZERO;
        private BigDecimal investedArs = BigDecimal.ZERO;
        private BigDecimal quantity = BigDecimal.ZERO;
        private boolean quantityKnown = true;
        private long purchases;
    }
}
