package com.tomas.cuaderno.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cuaderno.gemini")
public class GeminiProperties {
    private String apiKey = "";
    private String model = "gemini-flash-lite-latest";
    private String markdownModel = "gemini-3.1-flash-lite";
    private int timeoutMs = 10000;
    private int markdownTimeoutMs = 120000;
    private String embeddingModel = "gemini-embedding-001";
    private int embeddingTimeoutMs = 15000;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public String getModel() { return model; }
    public void setModel(String value) { model = value; }
    public String getMarkdownModel() { return markdownModel; }
    public void setMarkdownModel(String value) { markdownModel = value; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int value) { timeoutMs = value; }
    public int getMarkdownTimeoutMs() { return markdownTimeoutMs; }
    public void setMarkdownTimeoutMs(int value) { markdownTimeoutMs = value; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String value) { embeddingModel = value; }
    public int getEmbeddingTimeoutMs() { return embeddingTimeoutMs; }
    public void setEmbeddingTimeoutMs(int value) { embeddingTimeoutMs = value; }
}
