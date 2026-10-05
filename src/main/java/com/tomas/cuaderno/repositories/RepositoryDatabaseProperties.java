package com.tomas.cuaderno.repositories;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component
@ConfigurationProperties(prefix = "repositories.databases")
public class RepositoryDatabaseProperties {
    private Map<String, Target> targets = new LinkedHashMap<>();
    public Map<String, Target> getTargets() { return targets; }
    public void setTargets(Map<String, Target> targets) { this.targets = targets; }
    public static class Target {
        private String host; private int port = 5432; private String database; private String username; private String password;
        public String getHost() { return host; } public void setHost(String v) { host = v; }
        public int getPort() { return port; } public void setPort(int v) { port = v; }
        public String getDatabase() { return database; } public void setDatabase(String v) { database = v; }
        public String getUsername() { return username; } public void setUsername(String v) { username = v; }
        public String getPassword() { return password; } public void setPassword(String v) { password = v; }
    }
}
