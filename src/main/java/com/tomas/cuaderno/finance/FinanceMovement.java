package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "finance_movements")
public class FinanceMovement extends AuditableEntity {
    @Column(name = "date", nullable = false) private LocalDate date;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private FinanceBucket bucket;
    @Column(name = "account_code", nullable = false, length = 80) private String accountCode;
    @Column(name = "item_code", nullable = false, length = 80) private String itemCode;
    @Column(name = "amount_ars", nullable = false, precision = 19, scale = 2) private BigDecimal amountArs;
    @Column(name = "exchange_rate_snapshot", nullable = false, precision = 19, scale = 8) private BigDecimal exchangeRateSnapshot;
    @Column(length = 1000) private String note;
    @Column(name = "balance_applied", nullable = false) private boolean balanceApplied;
    public LocalDate getDate() { return date; } public void setDate(LocalDate v) { date = v; }
    public FinanceBucket getBucket() { return bucket; } public void setBucket(FinanceBucket v) { bucket = v; }
    public String getAccountCode() { return accountCode; } public void setAccountCode(String v) { accountCode = v; }
    public String getItemCode() { return itemCode; } public void setItemCode(String v) { itemCode = v; }
    public BigDecimal getAmountArs() { return amountArs; } public void setAmountArs(BigDecimal v) { amountArs = v; }
    public BigDecimal getExchangeRateSnapshot() { return exchangeRateSnapshot; } public void setExchangeRateSnapshot(BigDecimal v) { exchangeRateSnapshot = v; }
    public String getNote() { return note; } public void setNote(String v) { note = v; }
    public boolean isBalanceApplied() { return balanceApplied; } public void setBalanceApplied(boolean v) { balanceApplied = v; }
}
