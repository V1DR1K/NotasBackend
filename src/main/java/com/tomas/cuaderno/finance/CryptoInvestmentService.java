package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.TreeMap;
import java.time.LocalDate;
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
        Map<String, PositionTotals> byAsset = new HashMap<>();
        for (CryptoInvestment item : activeInvestments) {
            List<CryptoSale> itemSales = salesByInvestment.getOrDefault(item.getId(), List.of());
            BigDecimal remainingArs = remainingBasisArs(item, itemSales);
            BigDecimal remainingUsd = remainingBasisUsd(item, itemSales);
            investedArs = investedArs.add(remainingArs);
            investedUsd = investedUsd.add(remainingUsd);
            realizedProfitUsd = realizedProfitUsd.add(activeProfitUsd(itemSales));
            PositionTotals totals = byAsset.computeIfAbsent(item.getAssetCode(), ignored -> new PositionTotals());
            totals.investedArs = totals.investedArs.add(remainingArs);
            totals.investedUsd = totals.investedUsd.add(remainingUsd);
            if (remainingUsd.signum() > 0) totals.purchases++;
            if (item.getQuantity() == null || item.getUnitPriceUsd() == null) {
                totals.quantityKnown = false;
            } else {
                totals.quantity = totals.quantity.add(remainingQuantity(item, itemSales));
            }
        }

        FinanceAccount account = cryptoAccount(owner, false);
        BigDecimal accountBalance = account.getBalanceArs();
        BigDecimal totalUsd = CryptoWallet.totalUsd(account, investedArs, investedUsd, rate.average());
        BigDecimal availableArs = accountBalance.subtract(money(investedArs)).max(BigDecimal.ZERO).setScale(ARS_SCALE, ROUNDING);
        List<CryptoDtos.Position> positions = byAsset.entrySet().stream()
                .filter(entry -> entry.getValue().investedUsd.signum() > 0)
                .sorted((left, right) -> right.getValue().investedUsd.compareTo(left.getValue().investedUsd))
                .map(entry -> new CryptoDtos.Position(entry.getKey(), CryptoAsset.label(entry.getKey()),
                        usd(entry.getValue().investedUsd), money(entry.getValue().investedArs),
                        entry.getValue().quantityKnown ? quantity(entry.getValue().quantity) : null,
                        entry.getValue().purchases))
                .toList();
        return new CryptoDtos.Summary(
                new CryptoDtos.MoneyResponse(money(investedArs), usd(investedUsd), rate.average()),
                new CryptoDtos.MoneyResponse(availableArs, usd(totalUsd.subtract(investedUsd).max(BigDecimal.ZERO)), rate.average()),
                usd(realizedProfitUsd),
                positions,
                list(owner),
                rate,
                account.isUsdBalanceEstimated() || account.getBalanceUsd() == null,
                performance(activeInvestments, salesByInvestment, totalUsd));
    }

    @Transactional
    public CryptoDtos.InvestmentResponse create(UUID owner, CryptoDtos.CreateRequest request) {
        String assetCode = CryptoAsset.parse(request.assetCode());
        FinanceAccount account = cryptoAccount(owner, true);
        BigDecimal legacyRate = account.getBalanceUsd() == null ? rates.usd(owner).average() : BigDecimal.ONE;
        BigDecimal amountUsd = usd(request.amountUsd());
        BigDecimal unitPriceUsd = price(request.unitPriceUsd());
        BigDecimal units = request.quantity() == null
                ? amountUsd.divide(unitPriceUsd, QUANTITY_SCALE, ROUNDING)
                : quantity(request.quantity());
        if (units.signum() <= 0 || integerDigits(units) > 10) throw new BadRequestException("Purchase amount and price produce an unsupported crypto quantity");
        BigDecimal openArs = openBasisArs(owner), openUsd = openBasisUsd(owner);
        CryptoWallet.initialize(account, openArs, openUsd, legacyRate);
        BigDecimal availableUsd = account.getBalanceUsd().subtract(openUsd);
        BigDecimal availableArs = account.getBalanceArs().subtract(openArs).setScale(ARS_SCALE, ROUNDING);
        if (availableUsd.signum() <= 0 || amountUsd.compareTo(availableUsd) > 0) throw new BadRequestException("Insufficient available balance in crypto account");
        BigDecimal bookRate = availableArs.divide(availableUsd, 8, ROUNDING);
        BigDecimal amountArs = amountUsd.compareTo(availableUsd) == 0 ? availableArs : money(amountUsd.multiply(bookRate));
        if (amountArs.signum() <= 0 || availableArs.signum() < 0 || amountArs.compareTo(availableArs) > 0) {
            throw new BadRequestException("Insufficient available balance in crypto account");
        }
        CryptoInvestment investment = new CryptoInvestment();
        investment.setOwnerId(owner);
        investment.setDate(request.date());
        investment.setAssetCode(assetCode);
        investment.setAmountUsd(amountUsd);
        investment.setAmountArs(amountArs);
        investment.setExchangeRateSnapshot(bookRate);
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
        BigDecimal unitPriceUsd = price(request.unitPriceUsd());
        List<CryptoSale> itemSales = sales.findByInvestmentIdInOrderByDateDescCreatedAtDesc(List.of(id));
        if (investment.getUnitPriceUsd() != null && investment.getUnitPriceUsd().compareTo(unitPriceUsd) == 0) return response(investment, itemSales);
        if (itemSales.stream().anyMatch(sale -> sale.getDeletedAt() == null)) throw new BadRequestException("Anulá las ventas de este lote antes de corregir el precio de compra. Sus costos y ganancias ya están registrados.");
        BigDecimal quantity = investment.getAmountUsd().divide(unitPriceUsd, QUANTITY_SCALE, ROUNDING);
        if (quantity.signum() <= 0 || integerDigits(quantity) > 10) throw new BadRequestException("Purchase amount and price produce an unsupported crypto quantity");
        investment.setUnitPriceUsd(unitPriceUsd);
        investment.setQuantity(quantity);
        return response(investment, itemSales);
    }

    @Transactional
    public CryptoDtos.SaleResponse sell(UUID owner, UUID id, CryptoDtos.SellRequest request) {
        FinanceAccount account = cryptoAccount(owner, true);
        CryptoInvestment investment = investments.findActiveForUpdate(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        return sellLocked(account, owner, investment, request.date(), request.quantity(), request.proceedsUsd(), request.note());
    }

    @Transactional
    public List<CryptoDtos.SaleResponse> sellPosition(UUID owner, String assetCode, CryptoDtos.SellPositionRequest request) {
        String normalizedAssetCode = CryptoAsset.parse(assetCode);
        FinanceAccount account = cryptoAccount(owner, true);
        List<CryptoInvestment> rows = investments.findActiveForUpdate(owner, normalizedAssetCode);
        if (rows.isEmpty()) throw new BadRequestException("No hay posiciones abiertas para vender de esta moneda.");

        Map<UUID, List<CryptoSale>> salesByInvestment = salesFor(rows);
        Map<CryptoInvestment, BigDecimal> quantities = new java.util.LinkedHashMap<>();
        BigDecimal totalQuantity = BigDecimal.ZERO;
        for (CryptoInvestment investment : rows) {
            List<CryptoSale> itemSales = salesByInvestment.getOrDefault(investment.getId(), List.of());
            if (investment.getQuantity() == null || investment.getUnitPriceUsd() == null) {
                if (remainingBasisUsd(investment, itemSales).signum() > 0) {
                    throw new BadRequestException("Completá el precio de compra pendiente antes de vender toda la posición.");
                }
                continue;
            }
            BigDecimal remaining = remainingQuantity(investment, itemSales);
            if (remaining.signum() > 0) {
                if (request.date().isBefore(investment.getDate())) throw new BadRequestException("La venta no puede ser anterior a una compra de esta posición.");
                quantities.put(investment, remaining);
                totalQuantity = totalQuantity.add(remaining);
            }
        }
        if (totalQuantity.signum() <= 0) throw new BadRequestException("No hay unidades disponibles para vender de esta moneda.");

        BigDecimal totalProceeds = usd(request.proceedsUsd());
        BigDecimal allocatedProceeds = BigDecimal.ZERO.setScale(USD_SCALE);
        List<CryptoDtos.SaleResponse> result = new ArrayList<>();
        int index = 0;
        for (var entry : quantities.entrySet()) {
            index++;
            BigDecimal proceeds = index == quantities.size()
                    ? totalProceeds.subtract(allocatedProceeds)
                    : usd(totalProceeds.multiply(entry.getValue()).divide(totalQuantity, 18, ROUNDING));
            if (proceeds.signum() <= 0) throw new BadRequestException("El total de la venta es demasiado pequeño para distribuirlo entre todos los lotes.");
            result.add(sellLocked(account, owner, entry.getKey(), request.date(), entry.getValue(), proceeds, request.note()));
            allocatedProceeds = allocatedProceeds.add(proceeds);
        }
        return result;
    }

    private CryptoDtos.SaleResponse sellLocked(FinanceAccount account, UUID owner, CryptoInvestment investment,
            LocalDate date, BigDecimal requestedQuantity, BigDecimal requestedProceeds, String note) {
        if (investment.getQuantity() == null || investment.getUnitPriceUsd() == null) {
            throw new BadRequestException("Completá el precio de compra antes de vender este lote.");
        }
        List<CryptoSale> activeSales = sales.findByInvestmentIdAndDeletedAtIsNull(investment.getId());
        BigDecimal remainingQuantity = remainingQuantity(investment, activeSales);
        BigDecimal soldQuantity = quantity(requestedQuantity);
        if (soldQuantity.signum() <= 0 || soldQuantity.compareTo(remainingQuantity) > 0) {
            throw new BadRequestException("La cantidad supera las unidades disponibles en esta compra.");
        }
        BigDecimal proceedsUsd = usd(requestedProceeds);
        BigDecimal remainingUsd = remainingBasisUsd(investment, activeSales);
        BigDecimal remainingArs = remainingBasisArs(investment, activeSales);
        boolean closesLot = soldQuantity.compareTo(remainingQuantity) == 0;
        BigDecimal costBasisUsd = closesLot ? remainingUsd
                : usd(remainingUsd.multiply(soldQuantity).divide(remainingQuantity, 18, ROUNDING));
        BigDecimal costBasisArs = closesLot ? remainingArs
                : money(remainingArs.multiply(soldQuantity).divide(remainingQuantity, 18, ROUNDING));
        if (costBasisUsd.signum() <= 0) throw new BadRequestException("Sale quantity is below the supported precision");

        if (date.isBefore(investment.getDate())) throw new BadRequestException("La venta no puede ser anterior a la compra.");
        BigDecimal bookRate = investment.getExchangeRateSnapshot();
        CryptoWallet.initialize(account, openBasisArs(owner), openBasisUsd(owner), bookRate);
        BigDecimal nextUsd = usd(account.getBalanceUsd().add(proceedsUsd).subtract(costBasisUsd));
        BigDecimal saleUnitPrice = proceedsUsd.divide(soldQuantity, PRICE_SCALE, ROUNDING);
        BigDecimal realizedProfitArs = money(proceedsUsd.multiply(bookRate)).subtract(costBasisArs);
        BigDecimal nextAccountBalance = money(account.getBalanceArs().add(realizedProfitArs));
        if (nextAccountBalance.signum() < 0) throw new BadRequestException("Sale would make the crypto account balance negative");
        account.setBalanceUsd(nextUsd);
        account.setBalanceArs(nextAccountBalance);
        account.setBalanceAsOf(Instant.now());

        CryptoSale sale = new CryptoSale();
        sale.setOwnerId(owner);
        sale.setInvestmentId(investment.getId());
        sale.setDate(date);
        sale.setQuantity(soldQuantity);
        sale.setProceedsUsd(proceedsUsd);
        sale.setUnitPriceUsd(saleUnitPrice);
        sale.setCostBasisUsd(costBasisUsd);
        sale.setCostBasisArs(costBasisArs);
        sale.setExchangeRateSnapshot(bookRate);
        sale.setNote(note);
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
        BigDecimal openArs = openBasisArs(owner), openUsd = openBasisUsd(owner);
        CryptoWallet.initialize(account, openArs, openUsd, sale.getExchangeRateSnapshot());
        BigDecimal nextUsd = usd(account.getBalanceUsd().subtract(sale.getProceedsUsd()).add(sale.getCostBasisUsd()));
        if (nextBalance.compareTo(openArs.add(sale.getCostBasisArs())) < 0 || nextUsd.compareTo(openUsd.add(sale.getCostBasisUsd())) < 0)
            throw new BadRequestException("Saldo disponible insuficiente para anular la venta. Los fondos recibidos ya fueron usados.");
        account.setBalanceUsd(nextUsd);
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

    @Transactional(readOnly = true)
    public CryptoDtos.InvestmentResponse get(UUID owner, UUID id) {
        CryptoInvestment item = investments.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"));
        return response(item, sales.findByInvestmentIdInOrderByDateDescCreatedAtDesc(List.of(id)));
    }

    private BigDecimal openBasisUsd(UUID owner) {
        List<CryptoInvestment> rows = investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner);
        Map<UUID, List<CryptoSale>> byInvestment = salesFor(rows);
        return rows.stream().map(item -> remainingBasisUsd(item, byInvestment.getOrDefault(item.getId(), List.of())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(USD_SCALE, ROUNDING);
    }

    private CryptoDtos.Performance performance(List<CryptoInvestment> rows, Map<UUID, List<CryptoSale>> saleRows, BigDecimal capital) {
        BigDecimal purchases = BigDecimal.ZERO, proceeds = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        long count = 0;
        Map<LocalDate, BigDecimal[]> daily = new TreeMap<>();
        Map<String, BigDecimal[]> byAsset = new TreeMap<>();
        for (CryptoInvestment item : rows) {
            purchases = purchases.add(item.getAmountUsd());
            for (CryptoSale sale : saleRows.getOrDefault(item.getId(), List.of())) {
                if (sale.getDeletedAt() != null) continue;
                count++; proceeds = proceeds.add(sale.getProceedsUsd()); cost = cost.add(sale.getCostBasisUsd());
                BigDecimal[] day = daily.computeIfAbsent(sale.getDate(), ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                day[0] = day[0].add(sale.getProceedsUsd()); day[1] = day[1].add(sale.getCostBasisUsd());
                BigDecimal[] asset = byAsset.computeIfAbsent(item.getAssetCode(), ignored -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                asset[0] = asset[0].add(sale.getProceedsUsd()); asset[1] = asset[1].add(sale.getCostBasisUsd());
            }
        }
        List<CryptoDtos.ProfitDay> evolution = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (var entry : daily.entrySet()) {
            BigDecimal profit = entry.getValue()[0].subtract(entry.getValue()[1]); cumulative = cumulative.add(profit);
            evolution.add(new CryptoDtos.ProfitDay(entry.getKey(), usd(entry.getValue()[0]), usd(entry.getValue()[1]), usd(profit), usd(cumulative)));
        }
        List<CryptoDtos.AssetPerformance> assets = byAsset.entrySet().stream().map(e -> new CryptoDtos.AssetPerformance(e.getKey(), CryptoAsset.label(e.getKey()),
                usd(e.getValue()[0].subtract(e.getValue()[1])), usd(e.getValue()[0]), usd(e.getValue()[1]))).toList();
        return new CryptoDtos.Performance(usd(capital), usd(purchases), usd(proceeds), usd(cost),
                cost.signum() == 0 ? null : proceeds.subtract(cost).multiply(new BigDecimal("100")).divide(cost, 4, ROUNDING), count, evolution, assets);
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
                investment.getId(), investment.getDate(), investment.getAssetCode(), CryptoAsset.label(investment.getAssetCode()),
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
