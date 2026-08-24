package com.tomas.cuaderno.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.security")
public class SecurityProperties {
    private String allowedOrigins = "http://localhost:3000";
    public String getAllowedOrigins() { return allowedOrigins; } public void setAllowedOrigins(String v) { allowedOrigins = v; }
}
