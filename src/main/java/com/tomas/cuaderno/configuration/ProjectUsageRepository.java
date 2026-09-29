package com.tomas.cuaderno.configuration;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectUsageRepository {
    private final JdbcTemplate jdbc;

    public ProjectUsageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean hasActiveRecords(UUID owner, String code) {
        String sql = """
            SELECT EXISTS (
                SELECT 1 FROM tasks WHERE owner_id = ? AND lower(project_code) = lower(?) AND deleted_at IS NULL
                UNION ALL SELECT 1 FROM notes WHERE owner_id = ? AND lower(project_code) = lower(?) AND deleted_at IS NULL
                UNION ALL SELECT 1 FROM files WHERE owner_id = ? AND lower(project_code) = lower(?) AND deleted_at IS NULL
                UNION ALL SELECT 1 FROM file_folders WHERE owner_id = ? AND lower(project_code) = lower(?) AND deleted_at IS NULL
                UNION ALL SELECT 1 FROM calendar_events WHERE owner_id = ? AND lower(project_code) = lower(?) AND deleted_at IS NULL
            )
            """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, owner, code, owner, code, owner, code, owner, code, owner, code));
    }
}
