package com.tomas.cuaderno.repositories;

import java.time.Instant;
import java.util.List;

public final class RepositoryStatusDtos {
    private RepositoryStatusDtos() {}

    public record Response(Instant checkedAt, Instant refreshAvailableAt, Instant manualRefreshAvailableAt, List<Project> projects) {}
    public record Project(String id, String name, List<Component> components) {}
    public record Component(String id, String label, String fullName, Pipeline pipeline, Deployment deployment) {}
    public record Pipeline(
            String status,
            String conclusion,
            String workflowName,
            String sha,
            Integer runNumber,
            Instant startedAt,
            Instant updatedAt,
            String url,
            boolean available,
            boolean stale) {}
    public record Deployment(
            String state,
            String health,
            String image,
            String imageId,
            Instant startedAt) {}
}
