package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.math.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true) public class FinanceAccountService {
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);
    private static final String CASH_ACCOUNT = "mercadopago";
    private final FinanceAccountRepository repository;
    private final CryptoInvestmentRepository investments;
    public FinanceAccountService(FinanceAccountRepository repository, CryptoInvestmentRepository investments) { this.repository = repository; this.investments = investments; }
    public List<FinanceDtos.AccountResponse> list(UUID owner) { return repository.findByOwnerIdAndActiveTrueAndDeletedAtIsNullOrderByTypeAscCodeAsc(owner).stream().map(this::response).toList(); }
    @Transactional public FinanceDtos.AccountResponse sync(UUID owner, String code, FinanceDtos.AccountSyncRequest request) {
        FinanceAccount account = lock(owner, code);
        BigDecimal next = request.balanceArs().setScale(2, RoundingMode.HALF_UP);
        validateBalance(owner, account, next);
        account.setBalanceArs(next); account.setBalanceAsOf(Instant.now());
        return response(account);
    }
    public FinanceAccountRepository.ActiveAccount findActive(UUID owner, String code) { return repository.findActiveReference(owner, code).orElseThrow(() -> new NotFoundException("Finance account not found")); }
    @Transactional public void applyMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount) { adjustMovement(owner, accountCode, bucket, amount, BigDecimal.ONE); }
    @Transactional public void reverseMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount) { adjustMovement(owner, accountCode, bucket, amount, BigDecimal.ONE.negate()); }
    private void adjustMovement(UUID owner, String accountCode, FinanceBucket bucket, BigDecimal amount, BigDecimal multiplier) {
        Map<String, BigDecimal> deltas = new HashMap<>();
        addMovement(deltas, accountCode, bucket, amount, multiplier);
        applyDeltas(owner, deltas);
    }

    @Transactional public void replaceMovement(UUID owner, FinanceMovement previous, String accountCode, FinanceBucket bucket, BigDecimal amount) {
        Map<String, BigDecimal> deltas = new HashMap<>();
        if (previous.isBalanceApplied()) addMovement(deltas, previous.getAccountCode(), previous.getBucket(), previous.getAmountArs(), BigDecimal.ONE.negate());
        addMovement(deltas, accountCode, bucket, amount, BigDecimal.ONE);
        applyDeltas(owner, deltas);
    }

    private void addMovement(Map<String, BigDecimal> deltas, String accountCode, FinanceBucket bucket, BigDecimal amount, BigDecimal multiplier) {
        if (amount == null || amount.signum() <= 0) throw new BadRequestException("El importe debe ser mayor a cero.");
        String code = accountCode.trim().toLowerCase(Locale.ROOT);
        BigDecimal delta = signedAmount(bucket, amount, multiplier);
        deltas.merge(code, delta, BigDecimal::add);
        if (!CASH_ACCOUNT.equals(code)) deltas.merge(CASH_ACCOUNT, delta.negate(), BigDecimal::add);
    }

    private FinanceAccount lock(UUID owner, String code) {
        return repository.findActiveForUpdate(owner, code).orElseThrow(() -> new NotFoundException("Finance account not found"));
    }

    private void applyDeltas(UUID owner, Map<String, BigDecimal> deltas) {
        // All paths lock Mercado Pago first, then investments by code.
        List<String> codes = deltas.keySet().stream().sorted(Comparator.comparing((String code) -> !CASH_ACCOUNT.equals(code)).thenComparing(Comparator.naturalOrder())).toList();
        Map<FinanceAccount, BigDecimal> balances = new LinkedHashMap<>();
        for (String code : codes) {
            FinanceAccount account = lock(owner, code);
            BigDecimal delta = deltas.get(code);
            if (delta.signum() == 0) continue;
            BigDecimal next = balance(account).add(delta).setScale(2, RoundingMode.HALF_UP);
            validateBalance(owner, account, next);
            balances.put(account, next);
        }
        Instant now = Instant.now();
        balances.forEach((account, next) -> { account.setBalanceArs(next); account.setBalanceAsOf(now); });
    }

    private void validateBalance(UUID owner, FinanceAccount account, BigDecimal next) {
        if (next.signum() < 0) throw new BadRequestException("Saldo insuficiente en " + account.getLabel() + ". Revisá el importe.");
        if (account.getType() == FinanceAccountType.CRYPTO && next.compareTo(investments.openCostBasisArs(owner)) < 0) {
            throw new BadRequestException("Saldo disponible insuficiente en Cripto. Vendé las posiciones antes de retirar ese dinero.");
        }
    }
    private BigDecimal signedAmount(FinanceBucket bucket, BigDecimal amount, BigDecimal multiplier) {
        if (bucket == FinanceBucket.INVESTED) throw new BadRequestException("Only income and expense movements are supported");
        return amount.multiply(bucket == FinanceBucket.INCOME ? multiplier : multiplier.negate());
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
