package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.errors.BadRequestException;

/** Shared validation and display rules for arbitrary USDT crypto pairs. */
public final class CryptoAsset {
    private CryptoAsset() {}

    public static String parse(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace("/", "").replaceAll("\\s+", "");
        if (!normalized.matches("[A-Z0-9]{1,15}USDT")) {
            throw new BadRequestException("Ingresá una moneda válida con cotización en USDT, por ejemplo BTC/USDT.");
        }
        String symbol = normalized.substring(0, normalized.length() - 4);
        if (symbol.chars().noneMatch(Character::isLetter)) {
            throw new BadRequestException("El símbolo de la moneda debe contener al menos una letra.");
        }
        return normalized;
    }

    public static String label(String assetCode) {
        String normalized = parse(assetCode);
        return normalized.substring(0, normalized.length() - 4) + "/USDT";
    }
}
