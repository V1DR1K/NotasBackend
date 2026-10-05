package com.tomas.cuaderno.repositories;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/repos")
public interface GithubActionsClient {
    @GetExchange("/{owner}/{repo}/actions/runs")
    WorkflowRuns listLatestRuns(
            @PathVariable("owner") String owner,
            @PathVariable("repo") String repo,
            @RequestParam("per_page") int perPage);

    record WorkflowRuns(@JsonProperty("workflow_runs") List<WorkflowRun> workflowRuns) {}

    record WorkflowRun(
            String name,
            String status,
            String conclusion,
            @JsonProperty("head_sha") String headSha,
            @JsonProperty("run_number") Integer runNumber,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("html_url") String htmlUrl) {}
}
