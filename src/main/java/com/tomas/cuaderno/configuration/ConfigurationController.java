package com.tomas.cuaderno.configuration;

import com.tomas.cuaderno.common.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/config")
public class ConfigurationController {
    private final ConfigurationService service;
    public ConfigurationController(ConfigurationService service) { this.service = service; }
    @GetMapping("/day-statuses") public List<ConfigurationDtos.ConfigOptionResponse> dayStatuses() { return service.list(CurrentUser.id(), ConfigKind.DAY_STATUS); }
    @PostMapping("/day-statuses") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createDayStatus(@Valid @RequestBody ConfigurationDtos.DayStatusRequest request) { return service.createDayStatus(CurrentUser.id(), request); }
    @PatchMapping("/day-statuses/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchDayStatus(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.DAY_STATUS, code, request); }
    @DeleteMapping("/day-statuses/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteDayStatus(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.DAY_STATUS, code); }
    @GetMapping("/day-feelings") public List<ConfigurationDtos.ConfigOptionResponse> dayFeelings() { return service.list(CurrentUser.id(), ConfigKind.DAY_FEELING); }
    @PostMapping("/day-feelings") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createDayFeeling(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.DAY_FEELING, request); }
    @PatchMapping("/day-feelings/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchDayFeeling(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.DAY_FEELING, code, request); }
    @DeleteMapping("/day-feelings/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteDayFeeling(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.DAY_FEELING, code); }
    @GetMapping("/finance-items") public List<ConfigurationDtos.ConfigOptionResponse> financeItems() { return service.list(CurrentUser.id(), ConfigKind.FINANCE_ITEM); }
    @PostMapping("/finance-items") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createFinanceItem(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.FINANCE_ITEM, request); }
    @PatchMapping("/finance-items/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchFinanceItem(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.FINANCE_ITEM, code, request); }
    @DeleteMapping("/finance-items/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteFinanceItem(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.FINANCE_ITEM, code); }
    @GetMapping("/note-categories") public List<ConfigurationDtos.ConfigOptionResponse> noteCategories() { return service.list(CurrentUser.id(), ConfigKind.NOTE_CATEGORY); }
    @PostMapping("/note-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createNoteCategory(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, request); }
    @PatchMapping("/note-categories/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchNoteCategory(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, code, request); }
    @DeleteMapping("/note-categories/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteNoteCategory(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, code); }
}
