package com.tomas.cuaderno.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiMarkdownService {
    private static final String UNAVAILABLE_MESSAGE = "No se pudo organizar el contenido ahora. Intentá de nuevo en unos segundos.";
    private static final String INCOMPLETE_MESSAGE = "Gemini devolvió una versión incompleta o quitó parte del texto. No se aplicó el resultado y el contenido original sigue intacto; volvé a intentar.";
    private static final int MAX_OUTPUT_TOKENS = 65_536;
    private static final Pattern CONTENT_TOKEN = Pattern.compile("[\\p{L}\\p{M}\\p{N}]+");

    private final GeminiProperties properties;
    private final ObjectMapper mapper;
    private final String prompt;
    private final RestClient client;

    public GeminiMarkdownService(
            GeminiProperties properties,
            ObjectMapper mapper,
            @Value("classpath:prompts/markdown-format-system.txt") Resource promptResource) {
        this.properties = properties;
        this.mapper = mapper;
        try {
            this.prompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load the Gemini Markdown prompt", ex);
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getTimeoutMs());
        factory.setReadTimeout(properties.getMarkdownTimeoutMs());
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public GeminiMarkdownDtos.Response format(GeminiMarkdownDtos.Request request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiUnavailableException(UNAVAILABLE_MESSAGE);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of("parts", List.of(Map.of("text", prompt))));
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt(request))))));
        payload.put("generationConfig", Map.of("temperature", 0.1, "maxOutputTokens", MAX_OUTPUT_TOKENS));

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + properties.getMarkdownModel() + ":generateContent";
        try {
            String body = client.post().uri(url).header("x-goog-api-key", properties.getApiKey()).body(payload).retrieve().body(String.class);
            return new GeminiMarkdownDtos.Response(extractMarkdown(body, request.content()));
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private String userPrompt(GeminiMarkdownDtos.Request request) {
        String title = request.title() == null || request.title().isBlank() ? "(sin título)" : request.title().trim();
        return "Tipo de contenido: " + request.kind() + "\nTítulo de referencia (contexto, no repetir por defecto): " + title
                + "\n\nContenido original (texto a organizar):\n" + request.content().trim();
    }

    private String extractMarkdown(String body, String originalContent) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode candidate = root.path("candidates").path(0);
            if ("MAX_TOKENS".equals(candidate.path("finishReason").asText())) {
                throw new AiUnavailableException(INCOMPLETE_MESSAGE);
            }
            StringBuilder generated = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) generated.append(part.path("text").asText(""));
            }
            String text = generated.toString().trim();
            if (text.startsWith("```") && text.endsWith("```")) {
                int firstLineBreak = text.indexOf('\n');
                if (firstLineBreak >= 0) text = text.substring(firstLineBreak + 1, text.length() - 3).trim();
            }
            if (text.isBlank()) throw new AiUnavailableException(UNAVAILABLE_MESSAGE);
            if (!preservesOriginalContent(originalContent, text)) throw new AiUnavailableException(INCOMPLETE_MESSAGE);
            return text;
        } catch (AiUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AiUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    private boolean preservesOriginalContent(String original, String generated) {
        Map<String, Integer> requiredTokens = tokenCounts(original);
        Map<String, Integer> generatedTokens = tokenCounts(generated);
        return requiredTokens.entrySet().stream()
                .allMatch(entry -> generatedTokens.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private Map<String, Integer> tokenCounts(String markdown) {
        String withoutMarkdownListMarkers = markdown.replaceAll("(?m)^[ \\t]{0,3}(?:#{1,6}[ \\t]+|(?:[-+*]|\\d+[.)])[ \\t]+)", "");
        Matcher matcher = CONTENT_TOKEN.matcher(withoutMarkdownListMarkers.toLowerCase(Locale.ROOT));
        Map<String, Integer> counts = new HashMap<>();
        while (matcher.find()) counts.merge(matcher.group(), 1, Integer::sum);
        return counts;
    }
}
