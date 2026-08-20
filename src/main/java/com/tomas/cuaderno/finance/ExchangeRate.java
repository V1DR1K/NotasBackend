package com.tomas.cuaderno.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "exchange_rates")
public class ExchangeRate {
    @Id @GeneratedValue private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 3) private String currency = "USD";
    @Column(nullable = false, precision = 19, scale = 8) private BigDecimal buy;
    @Column(nullable = false, precision = 19, scale = 8) private BigDecimal sell;
    @Column(name = "fetched_at", nullable = false) private Instant fetchedAt;
    @Column(nullable = false, length = 40) private String source;
    @Column(nullable = false) private Instant updatedAt;
    @Version private Long version;
    @PrePersist @PreUpdate void touch() { updatedAt = Instant.now(); }
    public UUID getOwnerId() { return ownerId; } public void setOwnerId(UUID v) { ownerId = v; }
    public String getCurrency() { return currency; } public BigDecimal getBuy() { return buy; } public void setBuy(BigDecimal v) { buy = v; }
    public BigDecimal getSell() { return sell; } public void setSell(BigDecimal v) { sell = v; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public Instant getFetchedAt() { return fetchedAt; } public void setFetchedAt(Instant v) { fetchedAt = v; }
}
