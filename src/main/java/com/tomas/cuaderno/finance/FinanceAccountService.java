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
        if (request.balanceArs() == null && request.balanceUsd() == null) throw new BadRequestException("Ingresá el saldo a corregir.");
        BigDecimal next;
        if (account.getType() == FinanceAccountType.CRYPTO && request.balanceUsd() != null) {
            BigDecimal openUsd = investments.openCostBasisUsd(owner);
            BigDecimal openArs = investments.openCostBasisArs(owner);
            BigDecimal totalUsd = request.balanceUsd().setScale(8, RoundingMode.HALF_UP);
            if (totalUsd.compareTo(openUsd) < 0) throw new BadRequestException("El saldo total en USD no puede ser menor al costo de las posiciones abiertas.");
            BigDecimal freeArs = account.getBalanceArs().subtract(openArs).max(BigDecimal.ZERO);
            if (totalUsd.compareTo(openUsd) > 0 && freeArs.signum() == 0)
                throw new BadRequestException("Registrá el ingreso P2P con su cotización para agregar dólares sin costo de entrada registrado.");
            // Correct the cash quantity without rewriting the recorded purchase cost.
            next = totalUsd.compareTo(openUsd) == 0 ? openArs : account.getBalanceArs();
            account.setBalanceUsd(totalUsd); account.setUsdBalanceEstimated(false);
        } else {
            if (request.balanceArs() == null || account.getType() == FinanceAccountType.CRYPTO && account.getBalanceUsd() != null)
                throw new BadRequestException("Corregí el saldo de Cripto en USD.");
            next = request.balanceArs().setScale(2, RoundingMode.HALF_UP);
        }
        validateBalance(owner, account, next);
        account.setBalanceArs(next); account.setBalanceAsOf(Instant.now());
        return response(account);
    }
    public FinanceAccountRepository.ActiveAccount findActive(UUID owner, String code) { return repository.findActiveReference(owner, code).orElseThrow(() -> new NotFoundException("Finance account not found")); }
    @Transactional public BigDecimal move(UUID owner, FinanceMovement previous, String code, FinanceBucket bucket, BigDecimal amount, BigDecimal rate) {
        Map<String, BigDecimal> ars = new HashMap<>();
        if (previous != null && previous.isBalanceApplied()) addMovement(ars, previous.getAccountCode(), previous.getBucket(), previous.getAmountArs(), BigDecimal.ONE.negate());
        if (code != null) addMovement(ars, code, bucket, amount, BigDecimal.ONE);
        List<String> codes = ars.keySet().stream().sorted(Comparator.comparing((String c) -> !CASH_ACCOUNT.equals(c)).thenComparing(Comparator.naturalOrder())).toList();
        Map<String, FinanceAccount> locked = new LinkedHashMap<>();
        for (String c : codes) locked.put(c, lock(owner, c));
        FinanceAccount crypto = locked.get("crypto");
        BigDecimal newBook = null;
        if (crypto != null) {
            BigDecimal openArs = investments.openCostBasisArs(owner), openUsd = investments.openCostBasisUsd(owner);
            CryptoWallet.initialize(crypto, openArs, openUsd, previous != null && "crypto".equals(previous.getAccountCode()) ? previous.getExchangeRateSnapshot() : rate);
            BigDecimal nextUsd = crypto.getBalanceUsd(), nextArs = crypto.getBalanceArs();
            if (previous != null && previous.isBalanceApplied() && "crypto".equals(previous.getAccountCode())) {
                BigDecimal oldUsd = previous.getCryptoAmountUsd() == null ? previous.getAmountArs().divide(previous.getExchangeRateSnapshot(), 8, RoundingMode.HALF_UP) : previous.getCryptoAmountUsd();
                BigDecimal oldBook = previous.getCryptoBookAmountArs() == null ? previous.getAmountArs() : previous.getCryptoBookAmountArs();
                BigDecimal sign = previous.getBucket() == FinanceBucket.INCOME ? BigDecimal.ONE : BigDecimal.ONE.negate();
                nextUsd = nextUsd.subtract(oldUsd.multiply(sign)); nextArs = nextArs.subtract(oldBook.multiply(sign));
            }
            if ("crypto".equals(code)) {
                BigDecimal dollars = amount.divide(rate, 8, RoundingMode.HALF_UP);
                if (dollars.signum() <= 0) throw new BadRequestException("El importe en USD está por debajo de la precisión admitida.");
                if (bucket == FinanceBucket.INCOME) { newBook = amount; nextUsd = nextUsd.add(dollars); nextArs = nextArs.add(amount); }
                else {
                    BigDecimal freeUsd = nextUsd.subtract(openUsd), freeArs = nextArs.subtract(openArs);
                    if (freeUsd.signum() <= 0 || dollars.compareTo(freeUsd) > 0) throw new BadRequestException("Saldo disponible insuficiente en USD. Vendé las posiciones antes de retirar ese dinero.");
                    newBook = dollars.compareTo(freeUsd) == 0 ? freeArs : freeArs.multiply(dollars).divide(freeUsd, 2, RoundingMode.HALF_UP);
                    nextUsd = nextUsd.subtract(dollars); nextArs = nextArs.subtract(newBook);
                }
            }
            if (nextUsd.compareTo(openUsd) < 0 || nextArs.compareTo(openArs) < 0) throw new BadRequestException("La operación dejaría un saldo disponible inválido en Cripto.");
            crypto.setBalanceUsd(nextUsd.setScale(8, RoundingMode.HALF_UP));
            ars.put("crypto", nextArs.subtract(crypto.getBalanceArs()));
        }
        Instant now = Instant.now();
        for (var entry : locked.entrySet()) {
            FinanceAccount account = entry.getValue();
            BigDecimal next = balance(account).add(ars.get(entry.getKey())).setScale(2, RoundingMode.HALF_UP);
            validateBalance(owner, account, next);
            account.setBalanceArs(next); account.setBalanceAsOf(now);
        }
        return newBook;
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
    private FinanceDtos.AccountResponse response(FinanceAccount account) { return new FinanceDtos.AccountResponse(account.getCode(), account.getLabel(), account.getType(), balance(account), account.getAnnualRatePercent(), account.getGrowthMode(), account.getBalanceAsOf(), account.getBalanceUsd(), account.isUsdBalanceEstimated()); }
    private BigDecimal balance(FinanceAccount account) {
        BigDecimal base = account.getBalanceArs();
        if (account.getGrowthMode() != FinanceAccountGrowthMode.DAILY_TNA || account.getAnnualRatePercent().signum() == 0) return money(base);
        long days = Math.max(0, ChronoUnit.DAYS.between(account.getBalanceAsOf(), Instant.now()));
        BigDecimal dailyRate = account.getAnnualRatePercent().divide(BigDecimal.valueOf(100), 12, RoundingMode.HALF_UP).divide(DAYS_PER_YEAR, 12, RoundingMode.HALF_UP);
        return money(base.multiply(BigDecimal.ONE.add(dailyRate).pow(Math.toIntExact(days)), new MathContext(24, RoundingMode.HALF_UP)));
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
