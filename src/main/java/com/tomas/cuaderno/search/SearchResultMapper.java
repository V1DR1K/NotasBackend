package com.tomas.cuaderno.search;

import com.tomas.cuaderno.calendar.CalendarEvent;
import com.tomas.cuaderno.day.DayEntry;
import com.tomas.cuaderno.files.FileMetadata;
import com.tomas.cuaderno.finance.FinanceMovement;
import com.tomas.cuaderno.notes.Note;
import com.tomas.cuaderno.task.Task;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

public final class SearchResultMapper {
    private SearchResultMapper() {}

    public static SearchDtos.Result from(Note note) {
        return of("notes", note.getId(), note.getTitle(), note.getBody(), note.getDate());
    }

    public static SearchDtos.Result from(DayEntry day) {
        return of("day", day.getId(), day.getFeeling(), day.getDescription(), day.getDate());
    }

    public static SearchDtos.Result from(FinanceMovement movement) {
        return of("finances", movement.getId(), movement.getItemCode(), movement.getNote(), movement.getDate());
    }

    public static SearchDtos.Result from(FileMetadata file) {
        LocalDate uploadedDate = file.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return of("files", file.getId(), file.getName(), file.getDescription(), uploadedDate);
    }

    public static SearchDtos.Result from(Task task) {
        LocalDate date = task.getDueDate() == null
                ? task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()
                : task.getDueDate();
        return of("tasks", task.getId(), task.getTitle(), task.getDetail(), date);
    }

    public static SearchDtos.Result from(CalendarEvent event) {
        return of("calendar", event.getId(), event.getDescription(), event.getCategoryCode(), event.getDate());
    }

    public static SearchDtos.Result of(String section, UUID id, String title, String detail, LocalDate date) {
        return new SearchDtos.Result(section, id, titleOrFallback(section, title, date), detail == null ? "" : detail, date);
    }

    private static String titleOrFallback(String section, String title, LocalDate date) {
        if (title != null && !title.isBlank()) return title.trim();
        String label = switch (section) {
            case "day" -> "Registro del día";
            case "finances" -> "Movimiento financiero";
            case "files" -> "Archivo";
            case "notes" -> "Nota";
            case "tasks" -> "Tarea";
            case "calendar" -> "Evento";
            default -> "Resultado";
        };
        return date == null ? label : label + " · " + date;
    }
}
