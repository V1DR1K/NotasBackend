package com.tomas.cuaderno.search;

import com.tomas.cuaderno.calendar.CalendarEvent;
import com.tomas.cuaderno.calendar.CalendarEventRepository;
import com.tomas.cuaderno.day.DayEntry;
import com.tomas.cuaderno.day.DayEntryRepository;
import com.tomas.cuaderno.files.FileMetadata;
import com.tomas.cuaderno.files.FileMetadataRepository;
import com.tomas.cuaderno.finance.FinanceMovement;
import com.tomas.cuaderno.finance.FinanceMovementRepository;
import com.tomas.cuaderno.notes.Note;
import com.tomas.cuaderno.notes.NoteRepository;
import com.tomas.cuaderno.task.Task;
import com.tomas.cuaderno.task.TaskRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.tomas.cuaderno.common.errors.BadRequestException;

@Service
public class SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MAX_RESULTS_PER_SECTION = 5;
    private static final int MAX_RESULTS = 30;
    private static final int MAX_QUERY_LENGTH = 120;
    private final NoteRepository notes;
    private final DayEntryRepository days;
    private final FinanceMovementRepository movements;
    private final FileMetadataRepository files;
    private final TaskRepository tasks;
    private final CalendarEventRepository events;
    private final GeminiEmbeddingService embeddings;
    private final JdbcTemplate jdbc;

    public SearchService(
            NoteRepository notes,
            DayEntryRepository days,
            FinanceMovementRepository movements,
            FileMetadataRepository files,
            TaskRepository tasks,
            CalendarEventRepository events,
            GeminiEmbeddingService embeddings,
            JdbcTemplate jdbc) {
        this.notes = notes;
        this.days = days;
        this.movements = movements;
        this.files = files;
        this.tasks = tasks;
        this.events = events;
        this.embeddings = embeddings;
        this.jdbc = jdbc;
    }

    public List<SearchDtos.Result> search(UUID owner, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        if (query.length() < 2) return List.of();
        if (query.length() > MAX_QUERY_LENGTH) throw new BadRequestException("Search query is too long");

        List<SearchDtos.Result> literal = literalSearch(owner, query);
        if (!embeddings.isConfigured()) return literal;

        try {
            List<SearchDtos.Result> semantic = semanticSearch(owner, query);
            return merge(literal, semantic);
        } catch (Exception exception) {
            log.warn("Semantic search is temporarily unavailable ({}); using literal results",
                    exception.getClass().getSimpleName());
            return literal;
        }
    }

    private List<SearchDtos.Result> literalSearch(UUID owner, String query) {
        var results = new ArrayList<SearchDtos.Result>();
        var page = PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "date"));

        notes.findAll(textSpec(owner, query, "title", "body", "categoryCode", "projectCode"), page)
                .forEach(note -> results.add(noteResult(note)));
        days.findAll(textSpec(owner, query, "feeling", "description", "statusCode"), page)
                .forEach(day -> results.add(dayResult(day)));
        movements.findAll(textSpec(owner, query, "itemCode", "note", "accountCode"), page)
                .forEach(movement -> results.add(movementResult(movement)));
        files.findAll(textSpec(owner, query, "name", "description", "extension", "mimeType", "projectCode"),
                        PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(file -> results.add(fileResult(file)));
        tasks.findAll(textSpec(owner, query, "title", "detail", "categoryCode", "projectCode"),
                        PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .forEach(task -> results.add(taskResult(task)));
        events.findAll(textSpec(owner, query, "description", "categoryCode", "projectCode"), page)
                .forEach(event -> results.add(eventResult(event)));
        return results;
    }

    private List<SearchDtos.Result> semanticSearch(UUID owner, String query) {
        float[] vector = embeddings.embed(List.of(query), "RETRIEVAL_QUERY").getFirst();
        String queryVector = vectorLiteral(vector);
        return jdbc.query("""
                WITH nearest AS (
                    SELECT source_type, source_id, title, detail, record_date,
                           1 - (embedding <=> CAST(? AS vector)) AS similarity
                    FROM semantic_search_documents
                    WHERE owner_id = ?
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT 250
                ), best_per_record AS (
                    SELECT DISTINCT ON (source_type, source_id)
                           source_type, source_id, title, detail, record_date, similarity
                    FROM nearest
                    ORDER BY source_type, source_id, similarity DESC
                )
                SELECT source_type, source_id, title, detail, record_date
                FROM best_per_record
                ORDER BY similarity DESC
                LIMIT 60
                """,
                (row, rowNum) -> new SearchDtos.Result(
                        row.getString("source_type"),
                        row.getObject("source_id", UUID.class),
                        row.getString("title"),
                        row.getString("detail"),
                        row.getObject("record_date", java.time.LocalDate.class)),
                queryVector, owner, queryVector);
    }

    private List<SearchDtos.Result> merge(List<SearchDtos.Result> literal, List<SearchDtos.Result> semantic) {
        Map<String, RankedResult> combined = new LinkedHashMap<>();
        for (int i = 0; i < literal.size(); i++) {
            SearchDtos.Result result = literal.get(i);
            combined.put(key(result), new RankedResult(result, 3.0 / (60 + i + 1)));
        }
        for (int i = 0; i < semantic.size(); i++) {
            SearchDtos.Result result = semantic.get(i);
            String key = key(result);
            RankedResult existing = combined.get(key);
            double semanticScore = 1.0 / (60 + i + 1);
            if (existing == null) combined.put(key, new RankedResult(result, semanticScore));
            else existing.score += semanticScore;
        }
        return combined.values().stream()
                .sorted(Comparator.comparingDouble((RankedResult result) -> result.score).reversed())
                .limit(MAX_RESULTS)
                .map(result -> result.result)
                .toList();
    }

    private <T> Specification<T> textSpec(UUID owner, String query, String... fields) {
        String escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, criteriaQuery, cb) -> {
            var ownerPredicate = cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
            var textPredicates = new jakarta.persistence.criteria.Predicate[fields.length];
            for (int index = 0; index < fields.length; index++) {
                textPredicates[index] = cb.like(cb.lower(root.get(fields[index]).as(String.class)), pattern, '\\');
            }
            return cb.and(ownerPredicate, cb.or(textPredicates));
        };
    }

    private String key(SearchDtos.Result result) { return result.section() + ":" + result.id(); }
    private SearchDtos.Result noteResult(Note note) { return new SearchDtos.Result("notes", note.getId(), note.getTitle(), note.getBody(), note.getDate()); }
    private SearchDtos.Result dayResult(DayEntry day) { return new SearchDtos.Result("day", day.getId(), day.getFeeling(), day.getDescription(), day.getDate()); }
    private SearchDtos.Result movementResult(FinanceMovement movement) { return new SearchDtos.Result("finances", movement.getId(), movement.getItemCode(), movement.getNote(), movement.getDate()); }
    private SearchDtos.Result fileResult(FileMetadata file) { return new SearchDtos.Result("files", file.getId(), file.getName(), file.getDescription(), file.getUploadedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()); }
    private SearchDtos.Result taskResult(Task task) { return new SearchDtos.Result("tasks", task.getId(), task.getTitle(), task.getDetail(), task.getDueDate() == null ? task.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate() : task.getDueDate()); }
    private SearchDtos.Result eventResult(CalendarEvent event) { return new SearchDtos.Result("calendar", event.getId(), event.getDescription(), event.getCategoryCode(), event.getDate()); }

    private static final class RankedResult {
        private final SearchDtos.Result result;
        private double score;
        private RankedResult(SearchDtos.Result result, double score) { this.result = result; this.score = score; }
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) result.append(',');
            result.append(vector[i]);
        }
        return result.append(']').toString();
    }
}
