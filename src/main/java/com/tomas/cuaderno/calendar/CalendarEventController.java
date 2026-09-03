package com.tomas.cuaderno.calendar;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class CalendarEventController {
    private final CalendarEventService service;

    public CalendarEventController(CalendarEventService service) { this.service = service; }

    @GetMapping
    public PageResponse<CalendarEventDtos.Response> list(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String categoryCode,
            @PageableDefault(size = 100, sort = "date", direction = Sort.Direction.ASC) Pageable page) {
        return service.list(CurrentUser.id(), date, from, to, categoryCode, page);
    }

    @GetMapping("/{id}")
    public CalendarEventDtos.Response get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEventDtos.Response create(@Valid @RequestBody CalendarEventDtos.CreateRequest request) { return service.create(CurrentUser.id(), request); }

    @PatchMapping("/{id}")
    public CalendarEventDtos.Response patch(@PathVariable UUID id, @Valid @RequestBody CalendarEventDtos.PatchRequest request) { return service.patch(CurrentUser.id(), id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
}
