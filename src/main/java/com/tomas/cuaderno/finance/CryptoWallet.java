package com.tomas.cuaderno.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** USD stays fixed after the first legacy estimate; new P2P entries record their actual rate. */
final class CryptoWallet {
    private CryptoWallet() {}
    static BigDecimal totalUsd(FinanceAccount account, BigDecimal openArs, BigDecimal openUsd, BigDecimal legacyRate) {
        if (account.getBalanceUsd() != null) return account.getBalanceUsd();
        return openUsd.add(account.getBalanceArs().subtract(openArs).max(BigDecimal.ZERO)
                .divide(legacyRate, 8, RoundingMode.HALF_UP)).setScale(8, RoundingMode.HALF_UP);
    }
    static void initialize(FinanceAccount account, BigDecimal openArs, BigDecimal openUsd, BigDecimal legacyRate) {
        if (account.getBalanceUsd() == null) {
            account.setBalanceUsd(totalUsd(account, openArs, openUsd, legacyRate));
            account.setUsdBalanceEstimated(account.getBalanceArs().signum() != 0);
        }
    }
}
