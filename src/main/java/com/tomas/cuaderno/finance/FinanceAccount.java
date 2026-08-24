package com.tomas.cuaderno.finance;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "finance_accounts")
public class FinanceAccount extends AuditableEntity {
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 160) private String label;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false, length = 20) private FinanceAccountType type;
    @Column(name = "balance_ars", nullable = false, precision = 19, scale = 2) private BigDecimal balanceArs;
    @Column(name = "annual_rate_percent", nullable = false, precision = 9, scale = 4) private BigDecimal annualRatePercent = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(name = "growth_mode", nullable = false, length = 30) private FinanceAccountGrowthMode growthMode;
    @Column(name = "balance_as_of", nullable = false) private Instant balanceAsOf;
    @Column(nullable = false) private boolean active = true;

    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getLabel() { return label; } public void setLabel(String v) { label = v; }
    public FinanceAccountType getType() { return type; } public void setType(FinanceAccountType v) { type = v; }
    public BigDecimal getBalanceArs() { return balanceArs; } public void setBalanceArs(BigDecimal v) { balanceArs = v; }
    public BigDecimal getAnnualRatePercent() { return annualRatePercent; } public void setAnnualRatePercent(BigDecimal v) { annualRatePercent = v; }
    public FinanceAccountGrowthMode getGrowthMode() { return growthMode; } public void setGrowthMode(FinanceAccountGrowthMode v) { growthMode = v; }
    public Instant getBalanceAsOf() { return balanceAsOf; } public void setBalanceAsOf(Instant v) { balanceAsOf = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
}
