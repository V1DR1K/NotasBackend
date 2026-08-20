package com.tomas.cuaderno.notes;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notes") public class NoteController {
    private final NoteService service; public NoteController(NoteService service) { this.service = service; }
    @GetMapping public PageResponse<NoteDtos.Response> list(@RequestParam(required = false) String categoryCode, @RequestParam(required = false) LocalDate date, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String search, @PageableDefault(size = 20, sort = "date", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable page) { return service.list(CurrentUser.id(), categoryCode, date, from, to, search, page); }
    @GetMapping("/{id}") public NoteDtos.Response get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public NoteDtos.Response create(@Valid @RequestBody NoteDtos.CreateRequest request) { return service.create(CurrentUser.id(), request); }
    @PatchMapping("/{id}") public NoteDtos.Response patch(@PathVariable UUID id, @Valid @RequestBody NoteDtos.PatchRequest request) { return service.patch(CurrentUser.id(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
}
