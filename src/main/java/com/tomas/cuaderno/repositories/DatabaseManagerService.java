package com.tomas.cuaderno.repositories;

import com.tomas.cuaderno.repositories.DatabaseManagerDtos.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DatabaseManagerService {
    private static final Map<String, String> LABELS = Map.of("scalegrams", "ScaleGrams", "whatplan", "Whatplan", "notes", "Notes");
    private static final Map<String, String> OWNER_ROLES = Map.of("scalegrams", "scalegrams_repository_owner", "whatplan", "whatplan_repository_admin", "notes", "notes_repository_owner");
    private static final ScheduledExecutorService TIMEOUTS = Executors.newScheduledThreadPool(1, task -> {
        Thread thread = new Thread(task, "database-script-timeout"); thread.setDaemon(true); return thread;
    });
    private final RepositoryDatabaseProperties properties;

    public DatabaseManagerService(RepositoryDatabaseProperties properties) { this.properties = properties; }

    public List<DatabaseTarget> targets() {
        return List.of(new DatabaseTarget("scalegrams", "ScaleGrams"), new DatabaseTarget("whatplan", "Whatplan"), new DatabaseTarget("notes", "Notes"));
    }

    public List<Table> tables(String project) {
        try (HikariDataSource source = dataSource(project); Connection connection = source.getConnection()) {
            List<Table> tables = new ArrayList<>();
            try (var statement = connection.prepareStatement("select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name");
                 var rows = statement.executeQuery()) {
                while (rows.next()) tables.add(table(connection, rows.getString(1)));
            }
            return tables;
        } catch (SQLException exception) { throw databaseError(exception); }
    }

    public TablePage rows(String project, String tableName, int page, int pageSize, Map<String, String> filters) {
        if (page < 0 || page > 10000 || pageSize < 1 || pageSize > 100) throw badRequest("La página o su tamaño no son válidos.");
        long offset = (long) page * pageSize;
        if (offset > 1_000_000) throw badRequest("La página solicitada supera el límite permitido.");
        try (HikariDataSource source = dataSource(project); Connection connection = source.getConnection()) {
            Table table = table(connection, tableName);
            String names = table.columns().stream().map(column -> quote(column.name())).collect(java.util.stream.Collectors.joining(", "));
            StringBuilder where = new StringBuilder();
            List<String> values = new ArrayList<>();
            if (filters != null) {
                if (filters.size() > 10) throw badRequest("Se permiten hasta 10 filtros.");
                for (var filter : filters.entrySet()) {
                    Column column = findColumn(table, filter.getKey());
                    if (column == null) throw badRequest("El filtro apunta a una columna desconocida.");
                    if (filter.getValue() == null || filter.getValue().length() > 200) throw badRequest("El valor del filtro no es válido.");
                    where.append(where.isEmpty() ? " where " : " and ").append("CAST(").append(quote(column.name())).append(" AS text) ILIKE ? ESCAPE '\\'");
                    values.add("%" + filter.getValue().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
                }
            }
            String filterSql = where.toString();
            long total;
            try (var count = connection.prepareStatement("select count(*) from public." + quote(tableName) + filterSql)) {
                bindStrings(count, values);
                try (var result = count.executeQuery()) { result.next(); total = result.getLong(1); }
            }
            String order = table.primaryKey().isEmpty() ? "" : " order by " + table.primaryKey().stream().map(DatabaseManagerService::quote).collect(java.util.stream.Collectors.joining(", "));
            String query = "select " + names + " from public." + quote(tableName) + filterSql + order + " limit ? offset ?";
            List<Map<String, Object>> resultRows = new ArrayList<>();
            try (var select = connection.prepareStatement(query)) {
                int index = bindStrings(select, values); select.setInt(index++, pageSize); select.setLong(index, offset);
                try (ResultSet result = select.executeQuery()) { while (result.next()) resultRows.add(readRow(result, table.columns())); }
            }
            return new TablePage(tableName, table.columns(), resultRows, page, pageSize, total, table.primaryKey().isEmpty());
        } catch (SQLException exception) { throw databaseError(exception); }
    }

    public MutationResult mutate(String project, String tableName, RowMutation request) {
        if (request == null || !request.confirmed()) throw badRequest("Confirmá el cambio antes de guardar.");
        String action = request.action().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("insert", "update", "delete").contains(action)) throw badRequest("La operación solicitada no está permitida.");
        try (HikariDataSource source = dataSource(project); Connection connection = source.getConnection()) {
            Table table = table(connection, tableName);
            if (table.primaryKey().isEmpty()) throw badRequest("Esta tabla no tiene clave primaria y es de solo lectura.");
            validatePrimaryKey(table, request.primaryKey(), action.equals("insert"));
            if (action.equals("delete")) {
                if (request.values() != null && !request.values().isEmpty()) throw badRequest("La eliminación sólo acepta la clave primaria.");
                try (var statement = connection.prepareStatement("delete from public." + quote(tableName) + primaryWhere(table))) {
                    bindPrimary(statement, table, request.primaryKey(), 1); return new MutationResult(action, statement.executeUpdate());
                }
            }
            if (request.values() == null || request.values().isEmpty()) throw badRequest("Indicá al menos una columna para guardar.");
            Map<String, Object> values = new LinkedHashMap<>(request.values());
            for (String name : values.keySet()) {
                Column column = findColumn(table, name);
                if (column == null || column.generated()) throw badRequest("La columna indicada no se puede modificar.");
                if (action.equals("update") && table.primaryKey().contains(name)) throw badRequest("La clave primaria no se puede modificar.");
            }
            if (action.equals("insert")) {
                String columns = values.keySet().stream().map(DatabaseManagerService::quote).collect(java.util.stream.Collectors.joining(", "));
                String binds = String.join(", ", java.util.Collections.nCopies(values.size(), "?"));
                try (var statement = connection.prepareStatement("insert into public." + quote(tableName) + " (" + columns + ") values (" + binds + ")")) {
                    bindValues(statement, values, table); return new MutationResult(action, statement.executeUpdate());
                }
            }
            String sets = values.keySet().stream().map(name -> quote(name) + " = ?").collect(java.util.stream.Collectors.joining(", "));
            try (var statement = connection.prepareStatement("update public." + quote(tableName) + " set " + sets + primaryWhere(table))) {
                int index = bindValues(statement, values, table); bindPrimary(statement, table, request.primaryKey(), index);
                return new MutationResult(action, statement.executeUpdate());
            }
        } catch (SQLException exception) { throw databaseError(exception); }
    }

    public ScriptResult execute(String project, ScriptRequest request) {
        if (request == null) throw badRequest("Falta el script.");
        boolean write = "write".equalsIgnoreCase(request.mode());
        if (!write && !"read".equalsIgnoreCase(request.mode())) throw badRequest("Elegí el modo de lectura o escritura.");
        if (write && !request.confirmed()) throw badRequest("Confirmá la ejecución de escritura.");
        List<String> script = PostgresScriptParser.split(request.script());
        long start = System.nanoTime(), deadline = start + TimeUnit.SECONDS.toNanos(30);
        AtomicBoolean expired = new AtomicBoolean(false);
        AtomicReference<Statement> current = new AtomicReference<>();
        var timeout = TIMEOUTS.schedule(() -> {
            expired.set(true); Statement active = current.get();
            if (active != null) try { active.cancel(); } catch (SQLException ignored) { }
        }, 30, TimeUnit.SECONDS);
        try (HikariDataSource source = dataSource(project); Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            connection.setReadOnly(!write);
            List<StatementResult> results = new ArrayList<>();
            for (String sql : script) {
                if (expired.get() || System.nanoTime() > deadline) throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "La ejecución superó los 30 segundos.");
                try (Statement statement = connection.createStatement()) {
                    current.set(statement); statement.setQueryTimeout(30); statement.setFetchSize(501);
                    boolean hasResult = statement.execute(sql);
                    if (expired.get()) throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "La ejecución superó los 30 segundos.");
                    if (hasResult) results.add(readScriptResult(statement.getResultSet()));
                    else results.add(new StatementResult(List.of(), List.of(), (long) Math.max(statement.getUpdateCount(), 0), false));
                } finally { current.set(null); }
            }
            if (expired.get()) throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "La ejecución superó los 30 segundos.");
            connection.commit();
            return new ScriptResult(results, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), true);
        } catch (SQLException exception) { throw databaseError(exception); }
        finally { timeout.cancel(true); }
    }

    private StatementResult readScriptResult(ResultSet result) throws SQLException {
        if (result == null) return new StatementResult(List.of(), List.of(), 0L, false);
        var metadata = result.getMetaData(); List<String> columns = new ArrayList<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) columns.add(metadata.getColumnLabel(i));
        List<Map<String, Object>> rows = new ArrayList<>(); boolean truncated = false;
        while (result.next()) {
            if (rows.size() == 500) { truncated = true; break; }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns.size(); i++) row.put(columns.get(i - 1), jsonValue(result.getObject(i)));
            rows.add(row);
        }
        return new StatementResult(columns, rows, null, truncated);
    }

    private Table table(Connection connection, String tableName) throws SQLException {
        if (tableName == null || tableName.isBlank() || tableName.length() > 63) throw badRequest("La tabla indicada no es válida.");
        try (var check = connection.prepareStatement("select 1 from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name=?")) {
            check.setString(1, tableName);
            try (var result = check.executeQuery()) { if (!result.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la tabla."); }
        }
        List<String> primary = new ArrayList<>();
        String primarySql = """
                select a.attname from pg_index i join pg_class t on t.oid=i.indrelid join pg_namespace n on n.oid=t.relnamespace
                join unnest(i.indkey) with ordinality k(attnum, ord) on true join pg_attribute a on a.attrelid=t.oid and a.attnum=k.attnum
                where n.nspname='public' and t.relname=? and i.indisprimary order by k.ord
                """;
        try (var statement = connection.prepareStatement(primarySql)) {
            statement.setString(1, tableName); try (var result = statement.executeQuery()) { while (result.next()) primary.add(result.getString(1)); }
        }
        List<Column> columns = new ArrayList<>();
        String columnSql = """
                select c.column_name, c.data_type, c.is_nullable, c.column_default, c.is_generated, c.is_identity
                from information_schema.columns c where c.table_schema='public' and c.table_name=? order by c.ordinal_position
                """;
        try (var statement = connection.prepareStatement(columnSql)) {
            statement.setString(1, tableName);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    String name = result.getString(1);
                    String defaultValue = result.getString(4);
                    boolean generated = !"NEVER".equals(result.getString(5)) || "YES".equals(result.getString(6))
                            || (defaultValue != null && defaultValue.startsWith("nextval("));
                    columns.add(new Column(name, result.getString(2), "YES".equals(result.getString(3)), defaultValue,
                            primary.contains(name), generated));
                }
            }
        }
        return new Table(tableName, primary, columns);
    }

    private HikariDataSource dataSource(String project) {
        if (!LABELS.containsKey(project)) throw badRequest("El proyecto indicado no está disponible.");
        var target = properties.getTargets().get(project);
        if (target == null || target.getHost() == null || target.getDatabase() == null || target.getUsername() == null || target.getPassword() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La conexión de este proyecto todavía no está configurada.");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + target.getHost() + ":" + target.getPort() + "/" + target.getDatabase());
        config.setUsername(target.getUsername()); config.setPassword(target.getPassword());
        config.setMaximumPoolSize(1); config.setMinimumIdle(0); config.setConnectionTimeout(5000); config.setPoolName("repository-" + project);
        config.setConnectionInitSql("SET ROLE " + OWNER_ROLES.get(project));
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La base de datos de " + LABELS.get(project) + " no está disponible.", exception);
        }
    }

    private static void validatePrimaryKey(Table table, Map<String, Object> key, boolean insert) {
        if (key == null) throw badRequest("Falta la clave primaria.");
        if (insert) return;
        if (key.size() != table.primaryKey().size() || !key.keySet().containsAll(table.primaryKey())) throw badRequest("La clave primaria no coincide con la tabla.");
    }
    private static String primaryWhere(Table table) { return " where " + table.primaryKey().stream().map(name -> quote(name) + " = ?").collect(java.util.stream.Collectors.joining(" and ")); }
    private static int bindPrimary(java.sql.PreparedStatement statement, Table table, Map<String, Object> key, int start) throws SQLException {
        int index = start; for (String name : table.primaryKey()) statement.setObject(index++, databaseValue(key.get(name), findColumn(table, name))); return index;
    }
    private static int bindValues(java.sql.PreparedStatement statement, Map<String, Object> values, Table table) throws SQLException {
        int index = 1; for (var value : values.entrySet()) statement.setObject(index++, databaseValue(value.getValue(), findColumn(table, value.getKey()))); return index;
    }
    private static Object databaseValue(Object value, Column column) throws SQLException {
        if (value == null || column == null || !(value instanceof String text)) return value;
        try {
            return switch (column.dataType()) {
                case "uuid" -> UUID.fromString(text);
                case "boolean" -> Boolean.valueOf(text);
                case "smallint", "integer", "bigint", "numeric", "decimal", "real", "double precision" -> new BigDecimal(text);
                case "date" -> Date.valueOf(text);
                case "timestamp without time zone" -> Timestamp.valueOf(text.replace('T', ' '));
                case "timestamp with time zone" -> java.time.OffsetDateTime.parse(text).toInstant();
                case "json", "jsonb" -> text;
                default -> text;
            };
        } catch (RuntimeException exception) { throw new SQLException("El valor no coincide con el tipo de la columna " + column.name() + "."); }
    }
    private static Map<String, Object> readRow(ResultSet result, List<Column> columns) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(); for (int i = 1; i <= columns.size(); i++) row.put(columns.get(i - 1).name(), jsonValue(result.getObject(i))); return row;
    }
    private static Object jsonValue(Object value) throws SQLException {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof java.sql.Array array) return array.getArray();
        return value.toString();
    }
    private static int bindStrings(java.sql.PreparedStatement statement, List<String> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) statement.setString(i + 1, values.get(i)); return values.size() + 1;
    }
    private static Column findColumn(Table table, String name) { return table.columns().stream().filter(column -> column.name().equals(name)).findFirst().orElse(null); }
    private static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException databaseError(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null && sqlState.startsWith("08")) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La base de datos no está disponible.");
        }
        String message = exception.getMessage() == null ? "La base de datos rechazó la operación." : exception.getMessage().split("\\n", 2)[0];
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
