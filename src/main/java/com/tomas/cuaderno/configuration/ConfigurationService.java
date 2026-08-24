package com.tomas.cuaderno.configuration;

import com.tomas.cuaderno.common.errors.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationService {
    private static final Set<String> DAY_STATUS_CODES = Set.of("green", "yellow", "red");
    private final ConfigItemRepository repository;
    public ConfigurationService(ConfigItemRepository repository) { this.repository = repository; }
    public List<ConfigurationDtos.ConfigOptionResponse> list(UUID owner, ConfigKind kind) { return repository.findByOwnerIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(owner, kind).stream().map(this::response).toList(); }
    @Transactional public ConfigurationDtos.ConfigOptionResponse createDayStatus(UUID owner, ConfigurationDtos.DayStatusRequest request) { requireCanonicalDayStatus(request.code()); return create(owner, ConfigKind.DAY_STATUS, request.code(), request.label(), request.emoji(), request.sortOrder(), true); }
    @Transactional public ConfigurationDtos.ConfigOptionResponse createOption(UUID owner, ConfigKind kind, ConfigurationDtos.OptionRequest request) { return create(owner, kind, request.code(), request.label(), null, request.sortOrder(), request.active()); }
    @Transactional public ConfigurationDtos.ConfigOptionResponse patch(UUID owner, ConfigKind kind, String code, ConfigurationDtos.PatchRequest request) {
        ConfigItem item = find(owner, kind, code); if (kind == ConfigKind.DAY_STATUS && request.active() != null && !request.active()) throw new BadRequestException("The day semaphore always requires three active colors"); if (request.label() != null) { if (request.label().isBlank()) throw new BadRequestException("label cannot be blank"); item.setLabel(request.label().trim()); } if (request.emoji() != null) item.setEmoji(request.emoji()); if (request.sortOrder() != null) item.setSortOrder(request.sortOrder()); if (request.active() != null) item.setActive(request.active()); return response(item);
    }
    @Transactional public void delete(UUID owner, ConfigKind kind, String code) { if (kind == ConfigKind.DAY_STATUS) throw new BadRequestException("The day semaphore always requires three colors"); ConfigItem item = find(owner, kind, code); item.setDeletedAt(Instant.now()); }
    public ConfigItem requireActive(UUID owner, ConfigKind kind, String code, String field) {
        if (code == null || code.isBlank()) throw new BadRequestException(field + " is required");
        return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).filter(ConfigItem::isActive).orElseThrow(() -> new BadRequestException("Unknown or inactive " + field));
    }
    public ConfigurationDtos.ConfigOptionResponse option(UUID owner, ConfigKind kind, String code) { return response(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).orElseThrow(() -> new NotFoundException("Configuration option not found"))); }
    private ConfigurationDtos.ConfigOptionResponse create(UUID owner, ConfigKind kind, String code, String label, String emoji, int sortOrder, boolean active) {
        if (repository.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) throw new BadRequestException("Configuration code already exists");
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code.trim()); item.setLabel(label.trim()); item.setEmoji(emoji); item.setSortOrder(sortOrder); item.setActive(active); return response(repository.save(item));
    }
    private ConfigItem find(UUID owner, ConfigKind kind, String code) { return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).orElseThrow(() -> new NotFoundException("Configuration option not found")); }
    private void requireCanonicalDayStatus(String code) { if (code == null || !DAY_STATUS_CODES.contains(code.trim().toLowerCase())) throw new BadRequestException("The day semaphore only supports green, yellow and red"); }
    private ConfigurationDtos.ConfigOptionResponse response(ConfigItem item) { return new ConfigurationDtos.ConfigOptionResponse(item.getCode(), item.getLabel(), item.getEmoji(), item.getSortOrder(), item.isActive()); }
}
