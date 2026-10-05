package com.tomas.cuaderno.repositories;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repositories")
public class RepositoryStatusProperties {
    private final Github github = new Github();
    private final Images images = new Images();

    public Github getGithub() { return github; }
    public Images getImages() { return images; }

    public static class Github {
        private String apiBaseUrl = "https://api.github.com";
        private String apiToken = "";
        private long timeoutMs = 5_000;
        private Duration cacheTtl = Duration.ofHours(4);

        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
        public Duration getTimeout() { return Duration.ofMillis(timeoutMs); }
        public Duration getCacheTtl() { return cacheTtl; }
        public void setCacheTtl(Duration cacheTtl) { this.cacheTtl = cacheTtl; }
    }

    public static class Images {
        private Path statusFile = Path.of("/var/lib/cuaderno/repository-images.json");

        public Path getStatusFile() { return statusFile; }
        public void setStatusFile(Path statusFile) { this.statusFile = statusFile; }
    }
}
