package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CryptoInvestmentService {
    private static final String CRYPTO_ACCOUNT = "crypto";
    private final CryptoInvestmentRepository investments;
    private final FinanceAccountRepository accounts;
    private final ExchangeRateService rates;

    public CryptoInvestmentService(CryptoInvestmentRepository investments, FinanceAccountRepository accounts, ExchangeRateService rates) {
        this.investments = investments;
        this.accounts = accounts;
        this.rates = rates;
    }

    public List<CryptoDtos.InvestmentResponse> list(UUID owner) {
        return investments.findByOwnerIdAndDeletedAtIsNullOrderByDateDescCreatedAtDesc(owner).stream().map(this::response).toList();
    }

    public CryptoDtos.Summary summary(UUID owner) {
        FinanceDtos.ExchangeRateResponse rate = rates.usd(owner);
        BigDecimal investedArs = money(investments.sumAmountArs(owner));
        BigDecimal investedUsd = usd(investments.sumAmountUsd(owner));
        BigDecimal accountBalance = accounts.findByOwnerIdAndCodeIgnoreCaseAndDeletedAtIsNull(owner, CRYPTO_ACCOUNT)
                .filter(FinanceAccount::isActive)
                .map(FinanceAccount::getBalanceArs)
                .orElseThrow(() -> new NotFoundException("Crypto account not found"));
        BigDecimal availableArs = accountBalance.subtract(investedArs).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        List<CryptoDtos.Position> positions = investments.summarizePositions(owner).stream()
                .map(row -> new CryptoDtos.Position(row.getAsset().name(), row.getAsset().label(), usd(row.getAmountUsd()), money(row.getAmountArs()), row.getPurchases()))
                .toList();
        return new CryptoDtos.Summary(
                new CryptoDtos.MoneyResponse(investedArs, investedUsd, rate.average()),
                new CryptoDtos.MoneyResponse(availableArs, usd(availableArs.divide(rate.average(), 8, RoundingMode.HALF_UP)), rate.average()),
                positions,
                list(owner),
                rate);
    }

    @Transactional
    public CryptoDtos.InvestmentResponse create(UUID owner, CryptoDtos.CreateRequest request) {
        CryptoAsset asset = CryptoAsset.parse(request.assetCode());
        FinanceDtos.ExchangeRateResponse rate = rates.usd(owner);
        BigDecimal amountUsd = request.amountUsd().setScale(8, RoundingMode.HALF_UP);
        BigDecimal amountArs = amountUsd.multiply(rate.average()).setScale(2, RoundingMode.HALF_UP);
        FinanceAccount account = accounts.findActiveForUpdate(owner, CRYPTO_ACCOUNT)
                .orElseThrow(() -> new NotFoundException("Crypto account not found"));
        BigDecimal allocatedArs = money(investments.sumAmountArs(owner));
        BigDecimal availableArs = account.getBalanceArs().subtract(allocatedArs).setScale(2, RoundingMode.HALF_UP);
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
        investment.setNote(request.note());
        return response(investments.save(investment));
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        investments.findByIdAndOwnerIdAndDeletedAtIsNull(id, owner)
                .orElseThrow(() -> new NotFoundException("Crypto investment not found"))
                .setDeletedAt(Instant.now());
    }

    private CryptoDtos.InvestmentResponse response(CryptoInvestment investment) {
        return new CryptoDtos.InvestmentResponse(
                investment.getId(),
                investment.getDate(),
                investment.getAsset().name(),
                investment.getAsset().label(),
                new CryptoDtos.MoneyResponse(investment.getAmountArs(), investment.getAmountUsd(), investment.getExchangeRateSnapshot()),
                investment.getNote(),
                investment.getCreatedAt());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal usd(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(8, RoundingMode.HALF_UP);
    }
}
