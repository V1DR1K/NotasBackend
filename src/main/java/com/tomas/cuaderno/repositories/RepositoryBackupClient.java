package com.tomas.cuaderno.repositories;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
@Component
@ConfigurationProperties(prefix = "repositories.backup-control")
public class RepositoryBackupClient {
    private String baseUrl; private String token;
    public Map<?, ?> status() { return request(null); }
    public Map<?, ?> start(String project) {
        if (!java.util.Set.of("scalegrams", "whatplan", "notes").contains(project)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El proyecto indicado no está disponible.");
        return request(project);
    }
    private Map<?, ?> request(String project) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "El control de backups no está configurado.");
        try {
            RestClient client = RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Repository-Backup-Token", token).build();
            if (project == null) return client.get().uri("/api/status").retrieve().body(Map.class);
            return client.post().uri("/api/backups/{project}", project).retrieve().body(Map.class);
        } catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo consultar el servicio de backups."); }
    }
    public String getBaseUrl() { return baseUrl; } public void setBaseUrl(String value) { baseUrl = value; }
    public String getToken() { return token; } public void setToken(String value) { token = value; }
}
