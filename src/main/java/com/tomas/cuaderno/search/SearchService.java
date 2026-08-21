package com.tomas.cuaderno.search;

import com.tomas.cuaderno.day.DayEntry;
import com.tomas.cuaderno.day.DayEntryRepository;
import com.tomas.cuaderno.files.FileMetadata;
import com.tomas.cuaderno.files.FileMetadataRepository;
import com.tomas.cuaderno.finance.FinanceMovement;
import com.tomas.cuaderno.finance.FinanceMovementRepository;
import com.tomas.cuaderno.notes.Note;
import com.tomas.cuaderno.notes.NoteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final int MAX_RESULTS_PER_SECTION = 5;
    private final NoteRepository notes;
    private final DayEntryRepository days;
    private final FinanceMovementRepository movements;
    private final FileMetadataRepository files;

    public SearchService(NoteRepository notes, DayEntryRepository days, FinanceMovementRepository movements, FileMetadataRepository files) {
        this.notes = notes;
        this.days = days;
        this.movements = movements;
        this.files = files;
    }

    public List<SearchDtos.Result> search(UUID owner, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        if (query.length() < 2) return List.of();
        var results = new ArrayList<SearchDtos.Result>();

        notes.findAll(textSpec(owner, query, "title", "body", "categoryCode"), PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "date")))
                .forEach(note -> results.add(noteResult(note)));
        days.findAll(textSpec(owner, query, "feeling", "description"), PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "date")))
                .forEach(day -> results.add(dayResult(day)));
        movements.findAll(textSpec(owner, query, "conceptCode", "categoryCode", "note"), PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "date")))
                .forEach(movement -> results.add(movementResult(movement)));
        files.findAll(textSpec(owner, query, "name", "extension", "mimeType"), PageRequest.of(0, MAX_RESULTS_PER_SECTION, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(file -> results.add(fileResult(file)));
        return results;
    }

    private <T> Specification<T> textSpec(UUID owner, String query, String... fields) {
        String pattern = "%" + query + "%";
        return (root, criteriaQuery, cb) -> {
            var ownerPredicate = cb.and(cb.equal(root.get("ownerId"), owner), cb.isNull(root.get("deletedAt")));
            var textPredicates = new jakarta.persistence.criteria.Predicate[fields.length];
            for (int index = 0; index < fields.length; index++) textPredicates[index] = cb.like(cb.lower(root.get(fields[index]).as(String.class)), pattern);
            return cb.and(ownerPredicate, cb.or(textPredicates));
        };
    }

    private SearchDtos.Result noteResult(Note note) { return new SearchDtos.Result("notes", note.getId(), note.getTitle(), note.getBody(), note.getDate()); }
    private SearchDtos.Result dayResult(DayEntry day) { return new SearchDtos.Result("day", day.getId(), day.getFeeling(), day.getDescription(), day.getDate()); }
    private SearchDtos.Result movementResult(FinanceMovement movement) { return new SearchDtos.Result("finances", movement.getId(), movement.getConceptCode(), movement.getNote() == null ? movement.getCategoryCode() : movement.getNote(), movement.getDate()); }
    private SearchDtos.Result fileResult(FileMetadata file) { return new SearchDtos.Result("files", file.getId(), file.getName(), file.getMimeType(), file.getUploadedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate()); }
}
