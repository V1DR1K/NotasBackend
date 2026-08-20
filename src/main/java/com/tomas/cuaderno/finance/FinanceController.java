package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/finance") public class FinanceController {
    private final FinanceService service; private final ExchangeRateService rates;
    public FinanceController(FinanceService service, ExchangeRateService rates) { this.service = service; this.rates = rates; }
    @GetMapping("/movements") public PageResponse<FinanceDtos.Response> list(@RequestParam(required = false) FinanceBucket bucket, @RequestParam(required = false) LocalDate date, @RequestParam(required = false) String conceptCode, @RequestParam(required = false) String categoryCode, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount, @PageableDefault(size = 20, sort = "date", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable page) { return service.list(CurrentUser.id(), bucket, date, conceptCode, categoryCode, from, to, minAmount, maxAmount, page); }
    @GetMapping("/movements/{id}") public FinanceDtos.Response get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }
    @PostMapping("/movements") @ResponseStatus(HttpStatus.CREATED) public FinanceDtos.Response create(@Valid @RequestBody FinanceDtos.CreateRequest request) { return service.create(CurrentUser.id(), request); }
    @PatchMapping("/movements/{id}") public FinanceDtos.Response patch(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.PatchRequest request) { return service.patch(CurrentUser.id(), id, request); }
    @DeleteMapping("/movements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
    @GetMapping("/summary") public FinanceDtos.Summary summary(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.summary(CurrentUser.id(), from, to); }
    @GetMapping("/exchange-rate/usd") public FinanceDtos.ExchangeRateResponse rate() { return rates.usd(CurrentUser.id()); }
    @PostMapping("/exchange-rate/usd") @PreAuthorize("hasRole('ADMIN')") public FinanceDtos.ExchangeRateResponse fallback(@Valid @RequestBody FinanceDtos.FallbackRequest request) { return rates.setFallback(CurrentUser.id(), request); }
}
