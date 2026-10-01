package com.tomas.cuaderno.configuration;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryUsageRepository {
    private final JdbcTemplate jdbc;

    public CategoryUsageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void moveRecords(UUID owner, String categoryCode, String fromProject, String toProject) {
        jdbc.update("UPDATE tasks SET project_code = ? WHERE owner_id = ? AND lower(category_code) = lower(?) AND lower(project_code) = lower(?)", toProject, owner, categoryCode, fromProject);
        jdbc.update("UPDATE notes SET project_code = ? WHERE owner_id = ? AND lower(category_code) = lower(?) AND lower(project_code) = lower(?)", toProject, owner, categoryCode, fromProject);
        jdbc.update("UPDATE calendar_events SET project_code = ? WHERE owner_id = ? AND lower(category_code) = lower(?) AND lower(project_code) = lower(?)", toProject, owner, categoryCode, fromProject);
    }
}
