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
    public Map<String, ConfigurationDtos.ConfigOptionResponse> index(UUID owner, ConfigKind kind) {
        return list(owner, kind).stream().collect(java.util.stream.Collectors.toMap(option -> option.code().toLowerCase(Locale.ROOT), option -> option, (left, right) -> left, LinkedHashMap::new));
    }
    public Map<String, ConfigurationDtos.ConfigOptionResponse> indexIncludingDeleted(UUID owner, ConfigKind kind) {
        return repository.findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(owner, kind).stream().map(this::response).collect(java.util.stream.Collectors.toMap(option -> option.code().toLowerCase(Locale.ROOT), option -> option, (left, right) -> left, LinkedHashMap::new));
    }
    @Transactional public ConfigurationDtos.ConfigOptionResponse createDayStatus(UUID owner, ConfigurationDtos.DayStatusRequest request) { requireCanonicalDayStatus(request.code()); return create(owner, ConfigKind.DAY_STATUS, request.code(), request.label(), request.emoji(), request.sortOrder(), true, null); }
    @Transactional public ConfigurationDtos.ConfigOptionResponse createOption(UUID owner, ConfigKind kind, ConfigurationDtos.OptionRequest request) { return create(owner, kind, request.code(), request.label(), null, request.sortOrder(), request.active(), financeType(kind, request.financeType())); }
    @Transactional public ConfigurationDtos.ConfigOptionResponse patch(UUID owner, ConfigKind kind, String code, ConfigurationDtos.PatchRequest request) {
        ConfigItem item = find(owner, kind, code); if (kind == ConfigKind.DAY_STATUS && request.active() != null && !request.active()) throw new BadRequestException("The day semaphore always requires three active colors"); if (kind == ConfigKind.FINANCE_ITEM && isTransfer(item) && ((request.active() != null && !request.active()) || (request.financeType() != null && request.financeType() != FinanceItemType.TRANSFER))) throw new BadRequestException("Transfer classification must remain available for investment accounts"); if (request.label() != null) { if (request.label().isBlank()) throw new BadRequestException("label cannot be blank"); item.setLabel(request.label().trim()); } if (request.emoji() != null) item.setEmoji(request.emoji()); if (request.sortOrder() != null) item.setSortOrder(request.sortOrder()); if (request.active() != null) item.setActive(request.active()); if (request.financeType() != null) item.setFinanceType(financeType(kind, request.financeType())); return response(item);
    }
    @Transactional public void delete(UUID owner, ConfigKind kind, String code) { if (kind == ConfigKind.DAY_STATUS) throw new BadRequestException("The day semaphore always requires three colors"); ConfigItem item = find(owner, kind, code); if (kind == ConfigKind.FINANCE_ITEM && isTransfer(item)) throw new BadRequestException("Transfer classification is required for investment accounts"); item.setDeletedAt(Instant.now()); }
    public ConfigItem requireActive(UUID owner, ConfigKind kind, String code, String field) {
        if (code == null || code.isBlank()) throw new BadRequestException(field + " is required");
        return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).filter(ConfigItem::isActive).orElseThrow(() -> new BadRequestException("Unknown or inactive " + field));
    }
    public ConfigurationDtos.ConfigOptionResponse option(UUID owner, ConfigKind kind, String code) { return response(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).orElseThrow(() -> new NotFoundException("Configuration option not found"))); }
    private ConfigurationDtos.ConfigOptionResponse create(UUID owner, ConfigKind kind, String code, String label, String emoji, int sortOrder, boolean active, FinanceItemType financeType) {
        if (repository.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) throw new BadRequestException("Configuration code already exists");
        ConfigItem item = new ConfigItem(); item.setOwnerId(owner); item.setKind(kind); item.setCode(code.trim()); item.setLabel(label.trim()); item.setEmoji(emoji); item.setSortOrder(sortOrder); item.setActive(active); item.setFinanceType(financeType); return response(repository.save(item));
    }
    private ConfigItem find(UUID owner, ConfigKind kind, String code) { return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code).orElseThrow(() -> new NotFoundException("Configuration option not found")); }
    private void requireCanonicalDayStatus(String code) { if (code == null || !DAY_STATUS_CODES.contains(code.trim().toLowerCase())) throw new BadRequestException("The day semaphore only supports green, yellow and red"); }
    private FinanceItemType financeType(ConfigKind kind, FinanceItemType type) { if (kind == ConfigKind.FINANCE_ITEM && type == null) throw new BadRequestException("financeType is required"); if (kind != ConfigKind.FINANCE_ITEM && type != null) throw new BadRequestException("financeType is only valid for finance items"); return type; }
    private boolean isTransfer(ConfigItem item) { return "transferencia".equalsIgnoreCase(item.getCode()); }
    private ConfigurationDtos.ConfigOptionResponse response(ConfigItem item) { return new ConfigurationDtos.ConfigOptionResponse(item.getCode(), item.getLabel(), item.getEmoji(), item.getSortOrder(), item.isActive(), item.getFinanceType()); }
}
