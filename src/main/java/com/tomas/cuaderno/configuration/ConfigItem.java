package com.tomas.cuaderno.configuration;

import com.tomas.cuaderno.common.audit.AuditableEntity;
import jakarta.persistence.*;

@Entity @Table(name = "config_options")
public class ConfigItem extends AuditableEntity {
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ConfigKind kind;
    @Column(nullable = false, length = 80) private String code;
    @Column(nullable = false, length = 160) private String label;
    @Column(length = 16) private String emoji;
    @Enumerated(EnumType.STRING) @Column(name = "finance_type", length = 20) private FinanceItemType financeType;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    public ConfigKind getKind() { return kind; } public void setKind(ConfigKind v) { kind = v; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getLabel() { return label; } public void setLabel(String v) { label = v; }
    public String getEmoji() { return emoji; } public void setEmoji(String v) { emoji = v; }
    public FinanceItemType getFinanceType() { return financeType; } public void setFinanceType(FinanceItemType v) { financeType = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
    public int getSortOrder() { return sortOrder; } public void setSortOrder(int v) { sortOrder = v; }
}
