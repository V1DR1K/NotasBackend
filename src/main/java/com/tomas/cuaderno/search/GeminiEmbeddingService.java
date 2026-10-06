package com.tomas.cuaderno.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomas.cuaderno.ai.GeminiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiEmbeddingService {
    private static final int DIMENSIONS = 768;
    private final GeminiProperties properties;
    private final ObjectMapper mapper;
    private final RestClient client;

    public GeminiEmbeddingService(GeminiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getEmbeddingTimeoutMs());
        factory.setReadTimeout(properties.getEmbeddingTimeoutMs());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public List<float[]> embed(List<String> texts, String taskType) {
        if (!isConfigured()) throw new IllegalStateException("Gemini embedding is not configured");
        if (texts.isEmpty()) return List.of();

        List<Map<String, Object>> requests = new ArrayList<>(texts.size());
        for (String text : texts) {
            Map<String, Object> content = Map.of("parts", List.of(Map.of("text", text)));
            requests.add(Map.of(
                    "model", "models/" + properties.getEmbeddingModel(),
                    "content", content,
                    "taskType", taskType,
                    "outputDimensionality", DIMENSIONS));
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + properties.getEmbeddingModel() + ":batchEmbedContents";
        try {
            String body = client.post()
                    .uri(url)
                    .header("x-goog-api-key", properties.getApiKey())
                    .body(Map.of("requests", requests))
                    .retrieve()
                    .body(String.class);
            JsonNode values = mapper.readTree(body).path("embeddings");
            if (!values.isArray() || values.size() != texts.size()) {
                throw new IllegalStateException("Unexpected Gemini embedding response");
            }
            List<float[]> result = new ArrayList<>(values.size());
            for (JsonNode embedding : values) {
                JsonNode vector = embedding.path("values");
                if (!vector.isArray() || vector.size() != DIMENSIONS) {
                    throw new IllegalStateException("Unexpected Gemini embedding dimensions");
                }
                float[] coordinates = new float[DIMENSIONS];
                for (int i = 0; i < DIMENSIONS; i++) coordinates[i] = (float) vector.get(i).asDouble();
                result.add(coordinates);
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Gemini embedding request failed", exception);
        }
    }
}
