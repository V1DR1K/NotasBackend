package com.tomas.cuaderno.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.security")
public class SecurityProperties {
    private String jwtSecret;
    private Duration expiration = Duration.ofHours(8);
    private String cookieName = "CUADERNO_AUTH";
    private boolean secureCookie;
    private String allowedOrigins = "http://localhost:3000";
    public String getJwtSecret() { return jwtSecret; } public void setJwtSecret(String v) { jwtSecret = v; }
    public Duration getExpiration() { return expiration; } public void setExpiration(Duration v) { expiration = v; }
    public String getCookieName() { return cookieName; } public void setCookieName(String v) { cookieName = v; }
    public boolean isSecureCookie() { return secureCookie; } public void setSecureCookie(boolean v) { secureCookie = v; }
    public String getAllowedOrigins() { return allowedOrigins; } public void setAllowedOrigins(String v) { allowedOrigins = v; }
}
