package com.tomas.cuaderno.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.security")
public class SecurityProperties {
    private String cookieName = "CUADERNO_AUTH";
    private boolean secureCookie;
    private String allowedOrigins = "http://localhost:3000";
    public String getCookieName() { return cookieName; } public void setCookieName(String v) { cookieName = v; }
    public boolean isSecureCookie() { return secureCookie; } public void setSecureCookie(boolean v) { secureCookie = v; }
    public String getAllowedOrigins() { return allowedOrigins; } public void setAllowedOrigins(String v) { allowedOrigins = v; }
}
