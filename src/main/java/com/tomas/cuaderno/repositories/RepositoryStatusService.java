package com.tomas.cuaderno.repositories;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomas.cuaderno.repositories.GithubActionsClient.WorkflowRun;
import com.tomas.cuaderno.repositories.GithubActionsClient.WorkflowRuns;
import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Component;
import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Deployment;
import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Pipeline;
import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Project;
import com.tomas.cuaderno.repositories.RepositoryStatusDtos.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class RepositoryStatusService {
    private static final Logger log = LoggerFactory.getLogger(RepositoryStatusService.class);
    private static final List<RepositorySource> SOURCES = List.of(
            new RepositorySource("scalegrams-frontend", "scalegrams", "ScaleGrams", "frontend", "Frontend", "V1DR1K", "kcalFrontend"),
            new RepositorySource("scalegrams-backend", "scalegrams", "ScaleGrams", "backend", "Backend", "V1DR1K", "kcalBackend"),
            new RepositorySource("whatplan-frontend", "whatplan", "Whatplan", "frontend", "Frontend", "V1DR1K", "whereFoodFrontend"),
            new RepositorySource("whatplan-backend", "whatplan", "Whatplan", "backend", "Backend", "V1DR1K", "whereFoodBackend"),
            new RepositorySource("notes-frontend", "notes", "Notes", "frontend", "Frontend", "V1DR1K", "NotesFrontend"),
            new RepositorySource("notes-backend", "notes", "Notes", "backend", "Backend", "V1DR1K", "NotasBackend"));

    private final GithubActionsClient github;
    private final RepositoryStatusProperties properties;
    private final ObjectMapper objectMapper;
    private final Object cacheLock = new Object();
    private volatile PipelineCache cache;

    public RepositoryStatusService(
            GithubActionsClient github,
            RepositoryStatusProperties properties,
            ObjectMapper objectMapper) {
        this.github = github;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Response getStatus() {
        PipelineCache current = cache;
        Instant now = Instant.now();
        if (current == null || !now.isBefore(current.checkedAt().plus(properties.getGithub().getCacheTtl()))) {
            synchronized (cacheLock) {
                current = cache;
                now = Instant.now();
                if (current == null || !now.isBefore(current.checkedAt().plus(properties.getGithub().getCacheTtl()))) {
                    current = fetchPipelines(current);
                    cache = current;
                }
            }
        }

        JsonNode imageStatus = readImageStatus();
        List<Project> projects = List.of(
                project("scalegrams", "ScaleGrams", current.pipelines(), imageStatus),
                project("whatplan", "Whatplan", current.pipelines(), imageStatus),
                project("notes", "Notes", current.pipelines(), imageStatus));
        return new Response(
                current.checkedAt(),
                current.checkedAt().plus(properties.getGithub().getCacheTtl()),
                projects);
    }

    private PipelineCache fetchPipelines(PipelineCache previous) {
        Map<String, Pipeline> pipelines = new LinkedHashMap<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Map.Entry<String, Pipeline>>> requests = SOURCES.stream()
                    .map(source -> CompletableFuture.supplyAsync(
                            () -> Map.entry(source.id(), loadPipeline(source, previous)), executor))
                    .toList();
            for (CompletableFuture<Map.Entry<String, Pipeline>> request : requests) {
                Map.Entry<String, Pipeline> result = request.join();
                pipelines.put(result.getKey(), result.getValue());
            }
        }
        return new PipelineCache(Instant.now(), Map.copyOf(pipelines));
    }

    private Pipeline loadPipeline(RepositorySource source, PipelineCache previous) {
        try {
            WorkflowRuns response = github.listLatestRuns(source.owner(), source.repository(), 1);
            List<WorkflowRun> runs = response == null || response.workflowRuns() == null
                    ? List.of()
                    : response.workflowRuns();
            if (runs.isEmpty()) {
                return new Pipeline("no_runs", null, null, null, null, null, null,
                        source.actionsUrl(), true, false);
            }
            WorkflowRun run = runs.get(0);
            return new Pipeline(
                    valueOr(run.status(), "unknown"),
                    run.conclusion(),
                    run.name(),
                    run.headSha(),
                    run.runNumber(),
                    run.createdAt(),
                    run.updatedAt(),
                    valueOr(run.htmlUrl(), source.actionsUrl()),
                    true,
                    false);
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Could not read the latest workflow run for {}", source.fullName());
            Pipeline old = previous == null ? null : previous.pipelines().get(source.id());
            if (old != null && old.available()) {
                return new Pipeline(old.status(), old.conclusion(), old.workflowName(), old.sha(),
                        old.runNumber(), old.startedAt(), old.updatedAt(), old.url(), true, true);
            }
            return new Pipeline("unavailable", null, null, null, null, null, null,
                    source.actionsUrl(), false, false);
        }
    }

    private Project project(String id, String name, Map<String, Pipeline> pipelines, JsonNode imageStatus) {
        List<Component> components = SOURCES.stream()
                .filter(source -> source.projectId().equals(id))
                .map(source -> new Component(
                        source.id(),
                        source.componentLabel(),
                        source.fullName(),
                        pipelines.getOrDefault(source.id(), unavailable(source)),
                        deployment(imageStatus, source)))
                .toList();
        return new Project(id, name, components);
    }

    private Deployment deployment(JsonNode root, RepositorySource source) {
        JsonNode status = root.path("repositories").path(source.projectId()).path(source.componentId());
        if (status.isMissingNode() || status.isNull()) {
            return new Deployment("unknown", null, null, null, null);
        }
        return new Deployment(
                valueOr(status.path("state").asText(null), "unknown"),
                status.path("health").asText(null),
                status.path("image").asText(null),
                status.path("imageId").asText(null),
                instant(status.path("startedAt").asText(null)));
    }

    private JsonNode readImageStatus() {
        var path = properties.getImages().getStatusFile();
        try {
            return Files.isRegularFile(path) ? objectMapper.readTree(path.toFile()) : objectMapper.createObjectNode();
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not read the repository image snapshot at {}", path);
            return objectMapper.createObjectNode();
        }
    }

    private static Pipeline unavailable(RepositorySource source) {
        return new Pipeline("unavailable", null, null, null, null, null, null,
                source.actionsUrl(), false, false);
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PipelineCache(Instant checkedAt, Map<String, Pipeline> pipelines) {}

    private record RepositorySource(
            String id,
            String projectId,
            String projectName,
            String componentId,
            String componentLabel,
            String owner,
            String repository) {
        String fullName() { return owner + "/" + repository; }
        String actionsUrl() { return "https://github.com/" + owner + "/" + repository + "/actions"; }
    }
}
