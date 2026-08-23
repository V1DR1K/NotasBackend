package com.tomas.cuaderno.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.gemini")
public class GeminiProperties {
    private String apiKey = "";
    private String model = "gemini-flash-lite-latest";
    private int timeoutMs = 10000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int value) { timeoutMs = value; }
}
