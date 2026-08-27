package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.math.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class FinanceAccountService {
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    private static final String CASH_ACCOUNT = "mercadopago";
    private final FinanceAccountRepository repository;
    public FinanceAccountService(FinanceAccountRepository repository) { this.repository = repository; }
    public List<FinanceDtos.AccountResponse> list(UUID owner) { return repository.findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(owner).stream().map(this::response).toList(); }
    @Transactional public FinanceDtos.AccountResponse sync(UUID owner, String code, FinanceDtos.AccountSyncRequest request) {
        FinanceAccount account = repository.findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(owner, code).filter(FinanceAccount::isActive).orElseThrow(() -> new NotFoundException("Finance account not found"));
        account.setBalanceArs(request.balanceArs().setScale(2, RoundingMode.HALF_UP)); account.setBalanceAsOf(Instant.now());
        return response(account);
    }
    public FinanceAccount findActive(UUID owner, String code) { return repository.findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(owner, code).filter(FinanceAccount::isActive).orElseThrow(() -> new NotFoundException("Finance account not found")); }
    @Transactional public void applyMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount) { adjustMovement(owner, accountCode, bucket, amount, BigDecimal.ONE); }
    @Transactional public void reverseMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount) { adjustMovement(owner, accountCode, bucket, amount, BigDecimal.ONE.negate()); }
    private void adjustMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount, BigDecimal multiplier) {
        FinanceAccount account = repository.findActiveForUpdate(owner, accountCode).orElseThrow(() -> new NotFoundException("Finance account not found"));
        if (account.getType() == FinanceAccountType.CASH) {
            adjust(account, signedAmount(bucket, amount, multiplier));
            return;
        }
        FinanceAccount cash = repository.findActiveForUpdate(owner, CASH_ACCOUNT).orElseThrow(() -> new NotFoundException("MercadoPago account not found"));
        BigDecimal investmentDelta = signedAmount(bucket, amount, multiplier);
        adjust(cash, investmentDelta.negate());
        adjust(account, investmentDelta);
    }
    private BigDecimal signedAmount(FinanceBucket bucket, BigDecimal amount, BigDecimal multiplier) {
        if (bucket == FinanceBucket.INVESTED) throw new BadRequestException("Only income and expense movements are supported");
        return amount.multiply(bucket == FinanceBucket.INCOME ? multiplier : multiplier.negate());
    }
    private void adjust(FinanceAccount account, BigDecimal delta) {
        BigDecimal current = balance(account);
        BigDecimal next = current.add(delta).setScale(2, RoundingMode.HALF_UP);
        if (next.signum() < 0) throw new BadRequestException("Insufficient balance in finance account");
        account.setBalanceArs(next);
        account.setBalanceAsOf(Instant.now());
    }
    private FinanceDtos.AccountResponse response(FinanceAccount account) { return new FinanceDtos.AccountResponse(account.getCode(), account.getLabel(), account.getType(), balance(account), account.getAnnualRatePercent(), account.getGrowthMode(), account.getBalanceAsOf()); }
    private BigDecimal balance(FinanceAccount account) {
        BigDecimal base = account.getBalanceArs();
        if (account.getGrowthMode() != FinanceAccountGrowthMode.DAILY_TNA || account.getAnnualRatePercent().signum() == 0) return money(base);
        long days = Math.max(0, ChronoUnit.DAYS.between(account.getBalanceAsOf(), Instant.now()));
        BigDecimal dailyRate = account.getAnnualRatePercent().divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP).divide(DAYS_PER_YEAR, 12, RoundingMode.HALF_UP);
        return money(base.multiply(BigDecimal.ONE.add(dailyRate).pow(Math.toIntExact(days)), new MathContext(24, RoundingMode.HALF_UP)));
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
