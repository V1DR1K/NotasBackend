package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "crypto_sales")
public class CryptoSale extends AuditableEntity {
    @Column(name = "investment_id", nullable = false)
    private UUID investmentId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 18)
    private BigDecimal quantity;

    @Column(name = "proceeds_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal proceedsUsd;

    @Column(name = "unit_price_usd", nullable = false, precision = 28, scale = 12)
    private BigDecimal unitPriceUsd;

    @Column(name = "cost_basis_usd", nullable = false, precision = 19, scale = 8)
    private BigDecimal costBasisUsd;

    @Column(name = "cost_basis_ars", nullable = false, precision = 19, scale = 2)
    private BigDecimal costBasisArs;

    @Column(name = "exchange_rate_snapshot", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRateSnapshot;

    @Column(length = 1000)
    private String note;

    public UUID getInvestmentId() { return investmentId; }
    public void setInvestmentId(UUID value) { investmentId = value; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate value) { date = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getProceedsUsd() { return proceedsUsd; }
    public void setProceedsUsd(BigDecimal value) { proceedsUsd = value; }
    public BigDecimal getUnitPriceUsd() { return unitPriceUsd; }
    public void setUnitPriceUsd(BigDecimal value) { unitPriceUsd = value; }
    public BigDecimal getCostBasisUsd() { return costBasisUsd; }
    public void setCostBasisUsd(BigDecimal value) { costBasisUsd = value; }
    public BigDecimal getCostBasisArs() { return costBasisArs; }
    public void setCostBasisArs(BigDecimal value) { costBasisArs = value; }
    public BigDecimal getExchangeRateSnapshot() { return exchangeRateSnapshot; }
    public void setExchangeRateSnapshot(BigDecimal value) { exchangeRateSnapshot = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
}
