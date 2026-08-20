package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.NotFoundException;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.configuration.*;
import java.math.*;
import java.time.*;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class FinanceService {
    private final FinanceMovementRepository repository; private final ExchangeRateService rates; private final ConfigurationService configuration;
    public FinanceService(FinanceMovementRepository repository, ExchangeRateService rates, ConfigurationService configuration) { this.repository = repository; this.rates = rates; this.configuration = configuration; }
    public PageResponse<FinanceDtos.Response> list(UUID owner, FinanceBucket bucket, LocalDate date, String conceptCode, String categoryCode, LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount, Pageable page) {
        Specification<FinanceMovement> spec = (root, query, cb) -> cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
        if (bucket != null) spec = spec.and((r, q, c) -> c.equal(r.get("bucket"), bucket)); if (date != null) spec = spec.and((r, q, c) -> c.equal(r.get("date"), date)); if (conceptCode != null) spec = spec.and((r, q, c) -> c.equal(c.lower(r.get("conceptCode")), conceptCode.toLowerCase())); if (categoryCode != null) spec = spec.and((r, q, c) -> c.equal(c.lower(r.get("categoryCode")), categoryCode.toLowerCase())); if (from != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("date"), from)); if (to != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("date"), to)); if (minAmount != null) spec = spec.and((r, q, c) -> c.greaterThanOrEqualTo(r.get("amountArs"), minAmount)); if (maxAmount != null) spec = spec.and((r, q, c) -> c.lessThanOrEqualTo(r.get("amountArs"), maxAmount));
        return PageResponse.from(repository.findAll(spec, page).map(x -> response(owner, x)));
    }
    public FinanceDtos.Response get(UUID owner, UUID id) { return response(owner, repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found"))); }
    @Transactional public FinanceDtos.Response create(UUID owner, FinanceDtos.CreateRequest request) { validateCodes(owner, request.conceptCode(), request.categoryCode()); FinanceMovement x = new FinanceMovement(); x.setOwnerId(owner); x.setDate(request.date()); x.setBucket(request.bucket()); x.setConceptCode(request.conceptCode().trim()); x.setCategoryCode(request.categoryCode().trim()); x.setAmountArs(request.amountArs().setScale(2, RoundingMode.HALF_UP)); x.setExchangeRateSnapshot(rates.average(owner)); x.setNote(request.note()); return response(owner, repository.save(x)); }
    @Transactional public FinanceDtos.Response patch(UUID owner, UUID id, FinanceDtos.PatchRequest request) { FinanceMovement x = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found")); if (request.conceptCode() != null) { configuration.requireActive(owner, ConfigKind.FINANCE_CONCEPT, request.conceptCode(), "conceptCode"); x.setConceptCode(request.conceptCode().trim()); } if (request.categoryCode() != null) { configuration.requireActive(owner, ConfigKind.FINANCE_CATEGORY, request.categoryCode(), "categoryCode"); x.setCategoryCode(request.categoryCode().trim()); } if (request.date() != null) x.setDate(request.date()); if (request.bucket() != null) x.setBucket(request.bucket()); if (request.amountArs() != null) x.setAmountArs(request.amountArs().setScale(2, RoundingMode.HALF_UP)); if (request.note() != null) x.setNote(request.note()); return response(owner, x); }
    @Transactional public void delete(UUID owner, UUID id) { FinanceMovement x = repository.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner).orElseThrow(() -> new NotFoundException("Finance movement not found")); x.setDeletedAt(Instant.now()); }
    public FinanceDtos.Summary summary(UUID owner, LocalDate from, LocalDate to) { var list = list(owner, null, null, null, null, from, to, null, null, Pageable.unpaged()).content(); FinanceDtos.ExchangeRateResponse exchangeRate = rates.usd(owner); BigDecimal rate = exchangeRate.average(); BigDecimal income = sum(list, FinanceBucket.INCOME), expense = sum(list, FinanceBucket.EXPENSE), invested = sum(list, FinanceBucket.INVESTED); return new FinanceDtos.Summary(from, to, money(income, rate), money(expense, rate), money(invested, rate), money(income.subtract(expense).subtract(invested), rate), exchangeRate); }
    public long count(UUID owner) { return repository.countByOwnerIdAndDeletedAtIsNull(owner); }
    private void validateCodes(UUID owner, String concept, String category) { configuration.requireActive(owner, ConfigKind.FINANCE_CONCEPT, concept, "conceptCode"); configuration.requireActive(owner, ConfigKind.FINANCE_CATEGORY, category, "categoryCode"); }
    private BigDecimal sum(java.util.List<FinanceDtos.Response> list, FinanceBucket bucket) { return list.stream().filter(x -> x.bucket() == bucket).map(x -> x.amount().ars()).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private FinanceDtos.MoneyResponse money(BigDecimal ars, BigDecimal rate) { return new FinanceDtos.MoneyResponse(ars.setScale(2, RoundingMode.HALF_UP), ars.divide(rate, 2, RoundingMode.HALF_UP), rate); }
    private FinanceDtos.Response response(UUID owner, FinanceMovement x) { BigDecimal rate = x.getExchangeRateSnapshot(); return new FinanceDtos.Response(x.getId(), x.getDate(), x.getBucket(), money(x.getAmountArs(), rate), configuration.option(owner, ConfigKind.FINANCE_CONCEPT, x.getConceptCode()), configuration.option(owner, ConfigKind.FINANCE_CATEGORY, x.getCategoryCode()), x.getNote(), x.getCreatedAt(), x.getUpdatedAt()); }
}
