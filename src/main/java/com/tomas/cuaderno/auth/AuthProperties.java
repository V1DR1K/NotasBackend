package com.tomas.cuaderno.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.auth")
public class AuthProperties {
    private String serviceUrl;
    private String publicKeyPem;
    private String defaultRole = "USER";
    private String issuer = "central-auth-service";
    private String audience;
    private int clientTimeoutMs = 3000;

    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String value) { serviceUrl = value; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String value) { publicKeyPem = value; }
    public String getDefaultRole() { return defaultRole; }
    public void setDefaultRole(String value) { defaultRole = value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
    public int getClientTimeoutMs() { return clientTimeoutMs; }
    public void setClientTimeoutMs(int value) { clientTimeoutMs = value; }
}
