package com.tomas.cuaderno.day;

import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/day-entries") public class DayController {
    private final DayService service; public DayController(DayService service) { this.service = service; }
    @GetMapping public PageResponse<DayDtos.Response> list(@RequestParam(required = false) LocalDate date, @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to, @RequestParam(required = false) String statusCode, @PageableDefault(size = 20, sort = "date", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable page) { return service.list(CurrentUser.id(), date, from, to, statusCode, page); }
    @GetMapping("/{id}") public DayDtos.Response get(@PathVariable UUID id) { return service.get(CurrentUser.id(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public DayDtos.Response create(@Valid @RequestBody DayDtos.CreateRequest request) { return service.create(CurrentUser.id(), request); }
    @PatchMapping("/{id}") public DayDtos.Response patch(@PathVariable UUID id, @Valid @RequestBody DayDtos.PatchRequest request) { return service.patch(CurrentUser.id(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(CurrentUser.id(), id); }
}
