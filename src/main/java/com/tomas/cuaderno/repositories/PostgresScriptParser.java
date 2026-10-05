package com.tomas.cuaderno.repositories;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
public final class PostgresScriptParser {
    private static final Set<String> BLOCKED = Set.of("BEGIN", "START", "COMMIT", "END", "ROLLBACK", "ABORT", "SAVEPOINT", "RELEASE", "PREPARE", "EXECUTE", "DEALLOCATE", "CALL", "SET", "RESET", "DISCARD", "VACUUM", "COPY", "DO");
    private PostgresScriptParser() {}
    public static List<String> split(String sql) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("El script está vacío.");
        List<String> result = new ArrayList<>(); StringBuilder part = new StringBuilder();
        int single = 0, dbl = 0, line = 0, block = 0; String dollar = null;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i), n = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (line > 0) { part.append(c); if (c == '\n') line = 0; continue; }
            if (block > 0) { part.append(c); if (c == '/' && n == '*') { part.append(n); i++; block++; } else if (c == '*' && n == '/') { part.append(n); i++; block--; } continue; }
            if (dollar != null) { if (sql.startsWith(dollar, i)) { part.append(dollar); i += dollar.length() - 1; dollar = null; } else part.append(c); continue; }
            if (single > 0) { part.append(c); if (c == '\'' && n == '\'') { part.append(n); i++; } else if (c == '\'' && (i == 0 || sql.charAt(i - 1) != '\\')) single = 0; continue; }
            if (dbl > 0) { part.append(c); if (c == '"' && n == '"') { part.append(n); i++; } else if (c == '"') dbl = 0; continue; }
            if (c == '-' && n == '-') { part.append(c).append(n); i++; line = 1; continue; }
            if (c == '/' && n == '*') { part.append(c).append(n); i++; block = 1; continue; }
            if (c == '\'') { single = 1; part.append(c); continue; }
            if (c == '"') { dbl = 1; part.append(c); continue; }
            if (c == '$') { int end = sql.indexOf('$', i + 1); if (end >= 0 && sql.substring(i + 1, end).matches("[A-Za-z_][A-Za-z_0-9]*|")) { dollar = sql.substring(i, end + 1); part.append(dollar); i = end; continue; } }
            if (c == ';') { add(result, part.toString()); part.setLength(0); } else part.append(c);
        }
        if (single > 0 || dbl > 0 || block > 0 || dollar != null) throw new IllegalArgumentException("El script contiene una comilla o comentario sin cerrar.");
        add(result, part.toString());
        if (result.isEmpty()) throw new IllegalArgumentException("El script no contiene sentencias.");
        if (result.size() > 50) throw new IllegalArgumentException("Un script puede incluir hasta 50 sentencias.");
        for (String statement : result) {
            String keyword = firstKeyword(statement);
            if (BLOCKED.contains(keyword)) throw new IllegalArgumentException("El comando " + keyword + " no se admite dentro de una ejecución transaccional.");
            String upper = statement.toUpperCase(Locale.ROOT);
            if (keyword.equals("ALTER") && upper.matches("(?s).*\\bALTER\\s+SYSTEM\\b.*")) throw new IllegalArgumentException("ALTER SYSTEM no se admite desde el gestor.");
            if (keyword.equals("CREATE") && upper.matches("(?s).*\\bCREATE\\s+DATABASE\\b.*")) throw new IllegalArgumentException("CREATE DATABASE no se admite desde el gestor.");
        }
        return List.copyOf(result);
    }
    private static void add(List<String> result, String raw) { String value = raw.trim(); if (!value.isEmpty() && !value.replaceAll("(?s)/\\*.*?\\*/|--[^\\n]*", "").isBlank()) result.add(value); }
    private static String firstKeyword(String sql) {
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i), n = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (Character.isWhitespace(c) || c == ';') { i++; continue; }
            if (c == '-' && n == '-') { i += 2; while (i < sql.length() && sql.charAt(i) != '\n') i++; continue; }
            if (c == '/' && n == '*') { i += 2; int depth = 1; while (i < sql.length() && depth > 0) { if (i + 1 < sql.length() && sql.charAt(i) == '/' && sql.charAt(i + 1) == '*') { depth++; i += 2; } else if (i + 1 < sql.length() && sql.charAt(i) == '*' && sql.charAt(i + 1) == '/') { depth--; i += 2; } else i++; } continue; }
            if (!Character.isLetter(c)) return "";
            int start = i++; while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) i++;
            return sql.substring(start, i).toUpperCase(Locale.ROOT);
        }
        return "";
    }
}
