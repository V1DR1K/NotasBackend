package com.tomas.cuaderno.repositories;
import com.tomas.cuaderno.repositories.DatabaseManagerDtos.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/repositories/databases")
@PreAuthorize("hasRole('ADMIN')")
public class RepositoryDatabaseController {
    private final DatabaseManagerService databases; private final RepositoryBackupClient backups;
    public RepositoryDatabaseController(DatabaseManagerService databases, RepositoryBackupClient backups) { this.databases = databases; this.backups = backups; }
    @GetMapping public ResponseEntity<?> targets() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(databases.targets()); }
    @GetMapping("/{project}/tables") public ResponseEntity<?> tables(@PathVariable String project) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(databases.tables(project)); }
    @PostMapping("/{project}/tables/{table}/rows")
    public ResponseEntity<?> rows(@PathVariable String project, @PathVariable String table, @RequestBody(required = false) Map<String, Object> body) {
        int page = body != null && body.get("page") instanceof Number n ? n.intValue() : 0;
        int size = body != null && body.get("pageSize") instanceof Number n ? n.intValue() : 100;
        @SuppressWarnings("unchecked") Map<String, String> filters = body != null && body.get("filters") instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(databases.rows(project, table, page, size, filters));
    }
    @PatchMapping("/{project}/tables/{table}/rows")
    public MutationResult mutate(@PathVariable String project, @PathVariable String table, @Valid @RequestBody RowMutation request) { return databases.mutate(project, table, request); }
    @PostMapping("/{project}/query") public ScriptResult query(@PathVariable String project, @Valid @RequestBody ScriptRequest request) { return databases.execute(project, request); }
    @GetMapping("/backups") public ResponseEntity<?> backupStatus() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(backups.status()); }
    @PostMapping("/backups/{project}") public ResponseEntity<?> runBackup(@PathVariable String project) { return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(backups.start(project)); }
}
