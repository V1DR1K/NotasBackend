package com.tomas.cuaderno.repositories;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
public final class DatabaseManagerDtos {
    private DatabaseManagerDtos() {}
    public record DatabaseTarget(String id, String label) {}
    public record Column(String name, String dataType, boolean nullable, String defaultValue, boolean primaryKey, boolean generated) {}
    public record Table(String name, List<String> primaryKey, List<Column> columns) {}
    public record TablePage(String table, List<Column> columns, List<Map<String, Object>> rows, int page, int pageSize, long totalElements, boolean readOnly) {}
    public record RowMutation(@NotBlank String action, @NotNull Map<String, Object> primaryKey, @NotNull Map<String, Object> values, boolean confirmed) {}
    public record MutationResult(String action, int affectedRows) {}
    public record ScriptRequest(@NotBlank @Size(max = 100000) String script, @NotBlank String mode, boolean confirmed) {}
    public record StatementResult(List<String> columns, List<Map<String, Object>> rows, Long affectedRows, boolean truncated) {}
    public record ScriptResult(List<StatementResult> results, long elapsedMilliseconds, boolean committed) {}
}
