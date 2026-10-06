package com.tomas.cuaderno.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {
    @Mock NoteRepository notes;
    @Mock DayEntryRepository days;
    @Mock FinanceMovementRepository movements;
    @Mock FileMetadataRepository files;
    @Mock TaskRepository tasks;
    @Mock CalendarEventRepository events;
    @Mock GeminiEmbeddingService embeddings;
    private final StubJdbcTemplate jdbc = new StubJdbcTemplate();
    private SearchService service;

    @BeforeEach
    void createService() {
        service = new SearchService(notes, days, movements, files, tasks, events, embeddings, jdbc);
    }

    @Test
    void search_whenLiteralMatchesExist_shouldSearchAllSixSections() {
        UUID owner = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 6);
        Note note = mock(Note.class);
        DayEntry day = mock(DayEntry.class);
        FinanceMovement movement = mock(FinanceMovement.class);
        FileMetadata file = mock(FileMetadata.class);
        Task task = mock(Task.class);
        CalendarEvent event = mock(CalendarEvent.class);
        when(note.getId()).thenReturn(UUID.randomUUID());
        when(note.getTitle()).thenReturn("Tema guardado");
        when(note.getBody()).thenReturn("detalle");
        when(note.getDate()).thenReturn(date);
        when(day.getId()).thenReturn(UUID.randomUUID());
        when(day.getFeeling()).thenReturn("Tema del día");
        when(day.getDescription()).thenReturn("detalle");
        when(day.getDate()).thenReturn(date);
        when(movement.getId()).thenReturn(UUID.randomUUID());
        when(movement.getItemCode()).thenReturn("tema");
        when(movement.getNote()).thenReturn("detalle");
        when(movement.getDate()).thenReturn(date);
        when(file.getId()).thenReturn(UUID.randomUUID());
        when(file.getName()).thenReturn("Tema.txt");
        when(file.getDescription()).thenReturn("detalle");
        when(file.getUploadedAt()).thenReturn(Instant.parse("2026-10-06T12:00:00Z"));
        when(task.getId()).thenReturn(UUID.randomUUID());
        when(task.getTitle()).thenReturn("Tema pendiente");
        when(task.getDetail()).thenReturn("detalle");
        when(task.getDueDate()).thenReturn(date);
        when(event.getId()).thenReturn(UUID.randomUUID());
        when(event.getDescription()).thenReturn("Tema de agenda");
        when(event.getCategoryCode()).thenReturn("personal");
        when(event.getDate()).thenReturn(date);

        when(notes.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(note)));
        when(days.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(day)));
        when(movements.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(movement)));
        when(files.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(file)));
        when(tasks.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(task)));
        when(events.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));

        List<SearchDtos.Result> results = service.search(owner, "tema");

        assertThat(results).extracting(SearchDtos.Result::section)
                .containsExactly("notes", "day", "finances", "files", "tasks", "calendar");
        verify(notes).findAll(any(Specification.class), any(Pageable.class));
        verify(days).findAll(any(Specification.class), any(Pageable.class));
        verify(movements).findAll(any(Specification.class), any(Pageable.class));
        verify(files).findAll(any(Specification.class), any(Pageable.class));
        verify(tasks).findAll(any(Specification.class), any(Pageable.class));
        verify(events).findAll(any(Specification.class), any(Pageable.class));
        assertThat(jdbc.arguments).isNull();
    }

    @Test
    void search_whenGeminiIsConfigured_shouldMergeSemanticResultsAcrossAllSectionsForOwner() {
        UUID owner = UUID.randomUUID();
        List<SearchDtos.Result> semanticResults = List.of(
                result("notes"), result("day"), result("finances"),
                result("files"), result("tasks"), result("calendar"));
        jdbc.rows = semanticResults;
        when(embeddings.isConfigured()).thenReturn(true);
        when(embeddings.embed(List.of("meaning query"), "RETRIEVAL_QUERY")).thenReturn(List.of(new float[768]));
        stubEmptyLiteralResults();

        List<SearchDtos.Result> results = service.search(owner, "Meaning Query");

        assertThat(results).extracting(SearchDtos.Result::section)
                .containsExactly("notes", "day", "finances", "files", "tasks", "calendar");
        assertThat(jdbc.arguments).hasSize(3);
        assertThat(jdbc.arguments[1]).isEqualTo(owner);
        assertThat(jdbc.arguments[0]).isInstanceOf(String.class).asString().startsWith("[0.0,");
        assertThat(jdbc.arguments[2]).isEqualTo(jdbc.arguments[0]);
    }

    @Test
    void search_whenGeminiFails_shouldReturnLiteralResults() {
        UUID owner = UUID.randomUUID();
        Note note = mock(Note.class);
        when(note.getId()).thenReturn(UUID.randomUUID());
        when(note.getTitle()).thenReturn("Coincidencia literal");
        when(note.getBody()).thenReturn("detalle");
        when(note.getDate()).thenReturn(LocalDate.of(2026, 10, 6));
        when(notes.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(note)));
        stubEmptyLiteralResultsExceptNote();
        when(embeddings.isConfigured()).thenReturn(true);
        when(embeddings.embed(List.of("literal query"), "RETRIEVAL_QUERY"))
                .thenThrow(new IllegalStateException("Gemini unavailable"));

        List<SearchDtos.Result> results = service.search(owner, "literal query");

        assertThat(results).singleElement()
                .satisfies(result -> assertThat(result.section()).isEqualTo("notes"));
        assertThat(jdbc.arguments).isNull();
    }

    private void stubEmptyLiteralResults() {
        when(notes.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(days.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(movements.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(files.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(tasks.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(events.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    private void stubEmptyLiteralResultsExceptNote() {
        when(days.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(movements.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(files.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(tasks.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(events.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
    }

    private SearchDtos.Result result(String section) {
        return new SearchDtos.Result(section, UUID.randomUUID(), section + " title", section + " detail", LocalDate.of(2026, 10, 6));
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        private List<SearchDtos.Result> rows = List.of();
        private Object[] arguments;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            arguments = args;
            return (List<T>) rows;
        }
    }
}
