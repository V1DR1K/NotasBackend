package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;
import java.util.Arrays;

public enum CryptoAsset {
    BTCUSDT("BTC/USDT"),
    SOLUSDT("SOL/USDT"),
    ETHUSDT("ETH/USDT"),
    PEPEUSDT("PEPE/USDT");

    private final String label;

    CryptoAsset(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static CryptoAsset parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace("/", "");
        return Arrays.stream(values())
                .filter(asset -> asset.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported crypto asset"));
    }
}
