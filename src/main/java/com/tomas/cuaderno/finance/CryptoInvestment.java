package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "crypto_investments")
public class CryptoInvestment extends AuditableEntity {
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_code", nullable = false, length = 20)
    private CryptoAsset asset;

    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal amountUsd;

    @Column(name = "amount_ars", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountArs;

    @Column(name = "exchange_rate_snapshot", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRateSnapshot;

    @Column(name = "source_key", length = 80)
    private String sourceKey;

    @Column(length = 1000)
    private String note;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate value) { date = value; }
    public CryptoAsset getAsset() { return asset; }
    public void setAsset(CryptoAsset value) { asset = value; }
    public BigDecimal getAmountUsd() { return amountUsd; }
    public void setAmountUsd(BigDecimal value) { amountUsd = value; }
    public BigDecimal getAmountArs() { return amountArs; }
    public void setAmountArs(BigDecimal value) { amountArs = value; }
    public BigDecimal getExchangeRateSnapshot() { return exchangeRateSnapshot; }
    public void setExchangeRateSnapshot(BigDecimal value) { exchangeRateSnapshot = value; }
    public String getSourceKey() { return sourceKey; }
    public void setSourceKey(String value) { sourceKey = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
