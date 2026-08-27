package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class FinanceService {
    private static final int MAX_SUMMARY_DAYS = 366;
    private static final String CASH_ACCOUNT = "mercadopago";
    private static final String TRANSFER_ITEM = "transferencia";
    private static final Set<String> CASH_INCOME_ITEMS = Set.of("sueldo", "otro");
    private static final Set<String> CASH_EXPENSE_ITEMS = Set.of("pedidos_ya", "comida_afuera", "supermercado", "nafta", "uber_didi");
    private final FinanceMovementRepository repository; private final ExchangeRateService rates; private final ConfigurationService configuration; private final FinanceAccountService accounts;
    public FinanceService(FinanceMovementRepository repository, ExchangeRateService rates, ConfigurationService configuration, FinanceAccountService accounts) { this.repository = repository; this.rates = rates; this.configuration = configuration; this.accounts = accounts; }
    public PageResponse<FinanceDtos.Response> list(UUID owner, FinanceBucket bucket, LocalDate date, String itemCode, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount, Pageable page) {
        Specification<FinanceMovement> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (bucket != null) spec = spec.and((r, q, c) -> c.equal(r.get("bucket"), bucket)); if (date != null) spec = spec.and((r, q, c) -> c.equal(r.get("date"), date)); if (itemCode != null) spec = spec.and((r, q, c) -> c.equal(c.lower(r.get("itemCode")), itemCode.toLowerCase())); if (from != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("date"), from)); if (to != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("date"), to)); if (minAmount != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("amountArs"), minAmount)); if (maxAmount != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("amountArs"), maxAmount));
        Page<FinanceMovement> result = repository.findAll(spec, page);
        Map<String, ConfigurationDtos.ConfigOptionResponse> items = configuration.index(owner, ConfigKind.FINANCE_ITEM);
        return PageResponse.from(result.map(x -> response(x, items)));
    }
    public FinanceDtos.Response get(UUID owner, UUID id) { return response(owner, repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found"))); }
    @Transactional public FinanceDtos.Response create(UUID owner, FinanceDtos.CreateRequest request) { String accountCode = request.accountCode().trim(); String itemCode = request.itemCode().trim(); validateMovement(owner, accountCode, request.bucket(), itemCode); BigDecimal amount = request.amountArs().setScale(2, RoundingMode.HALF_UP); accounts.applyMovement(owner, accountCode, request.bucket(), amount); FinanceMovement x = new FinanceMovement(); x.setOwnerId(owner); x.setDate(request.date()); x.setBucket(request.bucket()); x.setAccountCode(accountCode); x.setItemCode(itemCode); x.setAmountArs(amount); x.setExchangeRateSnapshot(rates.average(owner)); x.setNote(request.note()); x.setBalanceApplied(true); return response(owner, repository.save(x)); }
    @Transactional public FinanceDtos.Response patch(UUID owner, UUID id, FinanceDtos.PatchRequest request) { FinanceMovement x = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found")); String accountCode = request.accountCode() == null ? x.getAccountCode() : request.accountCode().trim(); FinanceBucket bucket = request.bucket() == null ? x.getBucket() : request.bucket(); String itemCode = request.itemCode() == null ? x.getItemCode() : request.itemCode().trim(); BigDecimal amount = request.amountArs() == null ? x.getAmountArs() : request.amountArs().setScale(2, RoundingMode.HALF_UP); validateMovement(owner, accountCode, bucket, itemCode); boolean balanceChanged = !accountCode.equalsIgnoreCase(x.getAccountCode()) || bucket != x.getBucket() || amount.compareTo(x.getAmountArs()) != 0; if (balanceChanged && x.isBalanceApplied()) accounts.reverseMovement(owner, x.getAccountCode(), x.getBucket(), x.getAmountArs()); if (balanceChanged || !x.isBalanceApplied()) { accounts.applyMovement(owner, accountCode, bucket, amount); x.setBalanceApplied(true); } if (request.date() != null) x.setDate(request.date()); x.setBucket(bucket); x.setAccountCode(accountCode); x.setItemCode(itemCode); x.setAmountArs(amount); if (request.note() != null) x.setNote(request.note()); return response(owner, x); }
    @Transactional public void delete(UUID owner, UUID id) { FinanceMovement x = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found")); if (x.isBalanceApplied()) accounts.reverseMovement(owner, x.getAccountCode(), x.getBucket(), x.getAmountArs()); x.setDeletedAt(Instant.now()); }
    public FinanceDtos.Summary summary(UUID owner, LocalDate from, LocalDate to) {
        validateRange(from, to, "summary");
        BigDecimal income = BigDecimal.ZERO, expense = BigDecimal.ZERO, invested = BigDecimal.ZERO;
        for (FinanceMovementRepository.SummaryRow row : repository.summarize(owner, from, to)) {
            if (row.getBucket() == FinanceBucket.INCOME) income = row.getTotal();
            else if (row.getBucket() == FinanceBucket.EXPENSE) expense = row.getTotal();
            else if (row.getBucket() == FinanceBucket.INVESTED) invested = row.getTotal();
        }
        FinanceDtos.ExchangeRateResponse exchangeRate = rates.usd(owner); BigDecimal rate = exchangeRate.average();
        return new FinanceDtos.Summary(from, to, money(income, rate), money(expense, rate), money(invested, rate), money(income.subtract(expense).subtract(invested), rate), exchangeRate);
    }
    public FinanceDtos.Analytics analytics(UUID owner, LocalDate from, LocalDate to) {
        validateRange(from, to, "analytics");
        Map<LocalDate, EnumMap<FinanceBucket, BigDecimal>> daily = new TreeMap<>();
        for (FinanceMovementRepository.DailySummaryRow row : repository.summarizeDaily(owner, from, to)) {
            daily.computeIfAbsent(row.getDate(), ignored -> new EnumMap<>(FinanceBucket.class)).put(row.getBucket(), row.getTotal());
        }
        List<FinanceDtos.DailySummary> dailyResponse = daily.entrySet().stream()
            .map(entry -> new FinanceDtos.DailySummary(entry.getKey(), amount(entry.getValue(), FinanceBucket.INCOME), amount(entry.getValue(), FinanceBucket.EXPENSE)))
            .toList();
        Map<FinanceBucket, List<FinanceDtos.CategorySummary>> categories = new EnumMap<>(FinanceBucket.class);
        for (FinanceMovementRepository.CategorySummaryRow row : repository.summarizeCategories(owner, from, to)) {
            categories.computeIfAbsent(row.getBucket(), ignored -> new ArrayList<>()).add(new FinanceDtos.CategorySummary(row.getItemCode(), row.getTotal()));
        }
        return new FinanceDtos.Analytics(from, to, dailyResponse, categories.getOrDefault(FinanceBucket.INCOME, List.of()), categories.getOrDefault(FinanceBucket.EXPENSE, List.of()));
    }
    public long count(UUID owner) { return repository.countByOwnerIdAndDeletedAtIsNull(owner); }
    private void validateMovement(UUID owner, String accountCode, FinanceBucket bucket, String itemCode) { FinanceAccount account = accounts.findActive(owner, accountCode); if (bucket == FinanceBucket.INVESTED) throw new BadRequestException("Only income and expense movements are supported"); String normalizedAccount = account.getCode().toLowerCase(Locale.ROOT); String normalizedItem = itemCode.toLowerCase(Locale.ROOT); boolean valid = account.getType() == FinanceAccountType.CASH ? CASH_ACCOUNT.equals(normalizedAccount) && (bucket == FinanceBucket.INCOME ? CASH_INCOME_ITEMS.contains(normalizedItem) : CASH_EXPENSE_ITEMS.contains(normalizedItem)) : TRANSFER_ITEM.equals(normalizedItem); if (!valid) throw new BadRequestException("The movement classification is not valid for this account and type"); configuration.requireActive(owner, ConfigKind.FINANCE_ITEM, itemCode, "itemCode"); }
    private void validateRange(LocalDate from, LocalDate to, String operation) {
        if (from == null || to == null || from.isAfter(to)) throw new BadRequestException("Invalid finance " + operation + " range");
        if (from.plusDays(MAX_SUMMARY_DAYS - 1L).isBefore(to)) throw new BadRequestException("Finance " + operation + " range is too large");
    }
    private BigDecimal amount(Map<FinanceBucket, BigDecimal> values, FinanceBucket bucket) { return values.getOrDefault(bucket, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP); }
    private FinanceDtos.MoneyResponse money(BigDecimal ars, BigDecimal rate) { return new FinanceDtos.MoneyResponse(ars.setScale(2, RoundingMode.HALF_UP), ars.divide(rate, 2, RoundingMode.HALF_UP), rate); }
    private FinanceDtos.Response response(UUID owner, FinanceMovement x) { return response(x, configuration.index(owner, ConfigKind.FINANCE_ITEM)); }
    private FinanceDtos.Response response(FinanceMovement x, Map<String, ConfigurationDtos.ConfigOptionResponse> items) {
        BigDecimal rate = x.getExchangeRateSnapshot();
        ConfigurationDtos.ConfigOptionResponse item = items.get(x.getItemCode().toLowerCase(Locale.ROOT));
        if (item == null) throw new NotFoundException("Configuration option not found");
        return new FinanceDtos.Response(x.getId(), x.getDate(), x.getBucket(), x.getAccountCode(), money(x.getAmountArs(), rate), item, x.getNote(), x.getCreatedAt(), x.getUpdatedAt());
    }
}
