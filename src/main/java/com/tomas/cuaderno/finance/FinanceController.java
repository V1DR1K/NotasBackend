package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/finance") public class FinanceController {
    private final FinanceService service; private final FinanceAccountService accounts; private final ExchangeRateService rates; private final CryptoInvestmentService crypto;
    public FinanceController(FinanceService service, FinanceAccountService accounts, ExchangeRateService rates, CryptoInvestmentService crypto) { this.service = service; this.accounts = accounts; this.rates = rates; this.crypto = crypto; }
    @GetMapping("/movements") public PageResponse<FinanceDtos.Response> list(@RequestParam(required = false) FinanceMovementType movementType, @RequestParam(required = false) FinanceBucket bucket, @RequestParam(required = false) LocalDate date, @RequestParam(required = false) String itemCode, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) BigDecimal minAmount, @RequestParam(required = false) BigDecimal maxAmount, @PageableDefault(size = 20, sort = "date", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable page) { return service.list(CurrentUser.id(), bucket, date, itemCode, from, to, minAmount, maxAmount, page, movementType); }
    @GetMapping("/movements/{id}") public FinanceDtos.Response get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }
    @PostMapping("/movements") @ResponseStatus(HttpStatus.CREATED) public FinanceDtos.Response create(@Valid @RequestBody FinanceDtos.CreateRequest request) { return service.create(CurrentUser.id(), request); }
    @PostMapping("/transfers") @ResponseStatus(HttpStatus.CREATED) public FinanceDtos.Response transfer(@Valid @RequestBody FinanceDtos.TransferRequest request) { return service.transfer(CurrentUser.id(), request); }
    @PatchMapping("/transfers/{id}") public FinanceDtos.Response patchTransfer(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.TransferPatchRequest request) { return service.patchTransfer(CurrentUser.id(), id, request); }
    @PostMapping("/crypto/transfers") @ResponseStatus(HttpStatus.CREATED) public FinanceDtos.Response transferToCrypto(@Valid @RequestBody FinanceDtos.CryptoTransferRequest request) { return service.transferToCrypto(CurrentUser.id(), request); }
    @PatchMapping("/movements/{id}") public FinanceDtos.Response patch(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.PatchRequest request) { return service.patch(CurrentUser.id(), id, request); }
    @DeleteMapping("/movements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
    @GetMapping("/summary") public FinanceDtos.Summary summary(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.summary(CurrentUser.id(), from, to); }
    @GetMapping("/analytics") public FinanceDtos.Analytics analytics(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.analytics(CurrentUser.id(), from, to); }
    @GetMapping("/accounts") public List<FinanceDtos.AccountResponse> accounts() { return accounts.list(CurrentUser.id()); }
    @PutMapping("/accounts/{code}/balance") public FinanceDtos.AccountResponse syncAccount(@PathVariable String code, @Valid @RequestBody FinanceDtos.AccountSyncRequest request) { return accounts.sync(CurrentUser.id(), code, request); }
    @GetMapping("/exchange-rate/usd") public FinanceDtos.ExchangeRateResponse rate() { return rates.usd(CurrentUser.id()); }
    @GetMapping("/crypto/investments") public List<CryptoDtos.InvestmentResponse> cryptoInvestments() { return crypto.list(CurrentUser.id()); }
    @GetMapping("/crypto/summary") public CryptoDtos.Summary cryptoSummary() { return crypto.summary(CurrentUser.id()); }
    @PostMapping("/crypto/investments") @ResponseStatus(HttpStatus.CREATED) public CryptoDtos.InvestmentResponse invest(@Valid @RequestBody CryptoDtos.CreateRequest request) { return crypto.create(CurrentUser.id(), request); }
    @PatchMapping("/crypto/investments/{id}/unit-price") public CryptoDtos.InvestmentResponse completeCryptoPrice(@PathVariable UUID id, @Valid @RequestBody CryptoDtos.LegacyPriceRequest request) { return crypto.completeLegacyPrice(CurrentUser.id(), id, request); }
    @PostMapping("/crypto/investments/{id}/sales") @ResponseStatus(HttpStatus.CREATED) public CryptoDtos.SaleResponse sellCrypto(@PathVariable UUID id, @Valid @RequestBody CryptoDtos.SellRequest request) { return crypto.sell(CurrentUser.id(), id, request); }
    @PostMapping("/crypto/investments/{id}/sales/{saleId}/void") @ResponseStatus(HttpStatus.NO_CONTENT) public void voidCryptoSale(@PathVariable UUID id, @PathVariable UUID saleId) { crypto.voidSale(CurrentUser.id(), id, saleId); }
    @PostMapping("/crypto/investments/{id}/void") @ResponseStatus(HttpStatus.NO_CONTENT) public void voidInvestment(@PathVariable UUID id) { crypto.voidPurchase(CurrentUser.id(), id); }
    @DeleteMapping("/crypto/investments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteInvestment(@PathVariable UUID id) { crypto.voidPurchase(CurrentUser.id(), id); }
    @PostMapping("/exchange-rate/usd") @PreAuthorize("hasRole('ADMIN')") public FinanceDtos.ExchangeRateResponse fallback(@Valid @RequestBody FinanceDtos.FallbackRequest request) { return rates.setFallback(CurrentUser.id(), request); }
}
