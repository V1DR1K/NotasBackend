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
    @GetMapping("/finance-concepts") public List<ConfigurationDtos.ConfigOptionResponse> financeConcepts() { return service.list(CurrentUser.id(), ConfigKind.FINANCE_CONCEPT); }
    @PostMapping("/finance-concepts") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createFinanceConcept(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.FINANCE_CONCEPT, request); }
    @PatchMapping("/finance-concepts/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchFinanceConcept(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.FINANCE_CONCEPT, code, request); }
    @DeleteMapping("/finance-concepts/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteFinanceConcept(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.FINANCE_CONCEPT, code); }
    @GetMapping("/finance-categories") public List<ConfigurationDtos.ConfigOptionResponse> financeCategories() { return service.list(CurrentUser.id(), ConfigKind.FINANCE_CATEGORY); }
    @PostMapping("/finance-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createFinanceCategory(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.FINANCE_CATEGORY, request); }
    @PatchMapping("/finance-categories/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchFinanceCategory(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.FINANCE_CATEGORY, code, request); }
    @DeleteMapping("/finance-categories/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteFinanceCategory(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.FINANCE_CATEGORY, code); }
    @GetMapping("/note-categories") public List<ConfigurationDtos.ConfigOptionResponse> noteCategories() { return service.list(CurrentUser.id(), ConfigKind.NOTE_CATEGORY); }
    @PostMapping("/note-categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse createNoteCategory(@Valid @RequestBody ConfigurationDtos.OptionRequest request) { return service.createOption(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, request); }
    @PatchMapping("/note-categories/{code}") @PreAuthorize("hasRole('ADMIN')") public ConfigurationDtos.ConfigOptionResponse patchNoteCategory(@PathVariable String code, @Valid @RequestBody ConfigurationDtos.PatchRequest request) { return service.patch(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, code, request); }
    @DeleteMapping("/note-categories/{code}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteNoteCategory(@PathVariable String code) { service.delete(CurrentUser.id(), ConfigKind.NOTE_CATEGORY, code); }
}
