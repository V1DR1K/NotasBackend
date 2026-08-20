package com.tomas.cuaderno.finance;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.exchange-rate")
public class ExchangeRateProperties {
    private String providerUrl;
    private BigDecimal fallback = BigDecimal.ONE;
    private int timeoutMs = 3000;
    public String getProviderUrl() { return providerUrl; } public void setProviderUrl(String v) { providerUrl = v; }
    public BigDecimal getFallback() { return fallback; } public void setFallback(BigDecimal v) { fallback = v; }
    public int getTimeoutMs() { return timeoutMs; } public void setTimeoutMs(int v) { timeoutMs = v; }
}
