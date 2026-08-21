package com.tomas.cuaderno.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.auth")
public class AuthProperties {
    private String serviceUrl;
    private String publicKeyPem;
    private String defaultRole = "USER";
    private String refreshCookieName = "CUADERNO_REFRESH";
    private Duration refreshCookieMaxAge = Duration.ofDays(30);

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String value) { serviceUrl = value; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String value) { publicKeyPem = value; }
    public String getDefaultRole() { return defaultRole; }
    public void setDefaultRole(String value) { defaultRole = value; }
    public String getRefreshCookieName() { return refreshCookieName; }
    public void setRefreshCookieName(String value) { refreshCookieName = value; }
    public Duration getRefreshCookieMaxAge() { return refreshCookieMaxAge; }
    public void setRefreshCookieMaxAge(Duration value) { refreshCookieMaxAge = value; }
}
