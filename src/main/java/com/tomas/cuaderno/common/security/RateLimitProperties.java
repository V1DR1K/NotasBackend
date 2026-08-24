package com.tomas.cuaderno.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private long windowSeconds = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long value) { windowSeconds = value; }
}
