package com.tomas.cuaderno.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.tomas.cuaderno.repositories.DatabaseManagerDtos.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class DatabaseManagerServiceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");
    static DatabaseManagerService service;

    @BeforeAll static void setup() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE notes_repository_owner NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT");
            statement.execute("CREATE ROLE notes_repository_manager LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD 'manager-test-password'");
            statement.execute("GRANT notes_repository_owner TO notes_repository_manager");
            statement.execute("CREATE TABLE public.manager_test (id integer PRIMARY KEY, label text NOT NULL, amount integer)");
            statement.execute("ALTER TABLE public.manager_test OWNER TO notes_repository_owner");
            statement.execute("CREATE TABLE public.legacy_read_only (value text)");
            statement.execute("ALTER TABLE public.legacy_read_only OWNER TO notes_repository_owner");
        }
        var target = new RepositoryDatabaseProperties.Target();
        target.setHost(postgres.getHost()); target.setPort(postgres.getFirstMappedPort()); target.setDatabase(postgres.getDatabaseName());
        target.setUsername("notes_repository_manager"); target.setPassword("manager-test-password");
        var properties = new RepositoryDatabaseProperties(); properties.setTargets(Map.of("notes", target));
        service = new DatabaseManagerService(properties);
    }

    @BeforeEach void seed() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("TRUNCATE public.manager_test");
            statement.execute("INSERT INTO public.manager_test VALUES (1,'first',10),(2,'second',20)");
        }
    }

    @Test void listsMetadataAndPagesFilteredRowsInStablePrimaryKeyOrder() {
        Table metadata = service.tables("notes").stream().filter(table -> table.name().equals("manager_test")).findFirst().orElseThrow();
        assertEquals(List.of("id"), metadata.primaryKey());
        assertTrue(metadata.columns().stream().anyMatch(column -> column.name().equals("label") && !column.nullable()));
        TablePage page = service.rows("notes", "manager_test", 0, 1, Map.of());
        assertEquals(2, page.totalElements()); assertEquals(1, page.rows().size()); assertEquals(1, page.rows().getFirst().get("id"));
        TablePage filtered = service.rows("notes", "manager_test", 0, 100, Map.of("label","ond"));
        assertEquals(1, filtered.totalElements()); assertEquals("second", filtered.rows().getFirst().get("label"));
    }

    @Test void editsOnlyByPrimaryKeyAndRejectsTablesWithoutOne() {
        assertThrows(ResponseStatusException.class, () -> service.mutate("notes","manager_test",new RowMutation("update",Map.of("id",1),Map.of("label","x"),false)));
        assertEquals(1,service.mutate("notes","manager_test",new RowMutation("update",Map.of("id",1),Map.of("label","changed"),true)).affectedRows());
        assertEquals("changed",service.rows("notes","manager_test",0,100,Map.of()).rows().getFirst().get("label"));
        assertEquals(1,service.mutate("notes","manager_test",new RowMutation("insert",Map.of(),Map.of("id",3,"label","third","amount",30),true)).affectedRows());
        TablePage noKey=service.rows("notes","legacy_read_only",0,100,Map.of());
        assertTrue(noKey.readOnly());
        assertThrows(ResponseStatusException.class,()->service.mutate("notes","legacy_read_only",new RowMutation("delete",Map.of(),Map.of(),true)));
    }

    @Test void rollsBackEveryStatementWhenAScriptFailsAndEnforcesReadMode() {
        assertThrows(ResponseStatusException.class,()->service.execute("notes",new ScriptRequest(
                "insert into public.manager_test values (10,'temporary',1); insert into public.missing_table values (1)",
                "write",true)));
        assertEquals(2,service.rows("notes","manager_test",0,100,Map.of()).totalElements());
        assertThrows(ResponseStatusException.class,()->service.execute("notes",new ScriptRequest(
                "insert into public.manager_test values (11,'blocked',1)","read",false)));
        assertEquals(2,service.rows("notes","manager_test",0,100,Map.of()).totalElements());
    }

    @Test void capsSqlResultRowsAtFiveHundredAndRequiresWriteConfirmation() {
        assertThrows(ResponseStatusException.class,()->service.execute("notes",new ScriptRequest("delete from public.manager_test","write",false)));
        ScriptResult result=service.execute("notes",new ScriptRequest("select generate_series(1,501) as number","read",false));
        assertTrue(result.committed()); assertEquals(500,result.results().getFirst().rows().size()); assertTrue(result.results().getFirst().truncated());
    }
}
