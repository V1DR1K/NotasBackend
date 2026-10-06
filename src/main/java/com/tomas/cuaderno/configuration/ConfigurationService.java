package com.tomas.cuaderno.configuration;

import com.tomas.cuaderno.common.errors.BadRequestException;
import com.tomas.cuaderno.common.errors.NotFoundException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigurationService {
    private static final Set<String> DAY_STATUS_CODES = Set.of("green", "yellow", "red");

    private final ConfigItemRepository repository;
    private final ProjectUsageRepository projectUsage;
    private final CategoryUsageRepository categoryUsage;

    public ConfigurationService(
            ConfigItemRepository repository,
            ProjectUsageRepository projectUsage,
            CategoryUsageRepository categoryUsage) {
        this.repository = repository;
        this.projectUsage = projectUsage;
        this.categoryUsage = categoryUsage;
    }

    public List<ConfigurationDtos.ConfigOptionResponse> list(UUID owner, ConfigKind kind) {
        return repository.findByOwnerIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(owner, kind)
                .stream()
                .map(this::response)
                .toList();
    }

    public Map<String, ConfigurationDtos.ConfigOptionResponse> index(UUID owner, ConfigKind kind) {
        return list(owner, kind).stream()
                .collect(Collectors.toMap(
                        option -> option.code().toLowerCase(Locale.ROOT),
                        option -> option,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public Map<String, ConfigurationDtos.ConfigOptionResponse> indexIncludingDeleted(UUID owner, ConfigKind kind) {
        return repository.findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(owner, kind)
                .stream()
                .map(this::response)
                .collect(Collectors.toMap(
                        option -> option.code().toLowerCase(Locale.ROOT),
                        option -> option,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public Map<String, ConfigurationDtos.ConfigOptionResponse> categoriesIndexIncludingDeleted(UUID owner) {
        return repository.findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(owner, ConfigKind.CATEGORY)
                .stream()
                .collect(Collectors.toMap(
                        item -> categoryKey(item.getProjectCode(), item.getCode()),
                        this::response,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public List<ConfigurationDtos.CategoryResponse> listCategories(UUID owner, String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            return repository.findByOwnerIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(
                            owner, ConfigKind.CATEGORY)
                    .stream()
                    .map(this::categoryResponse)
                    .toList();
        }

        String canonicalProject = projectCode(owner, projectCode);
        return repository.findByOwnerIdAndKindAndProjectCodeIgnoreCaseAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(
                        owner, ConfigKind.CATEGORY, canonicalProject)
                .stream()
                .map(this::categoryResponse)
                .toList();
    }

    @Transactional
    public ConfigurationDtos.CategoryResponse createCategory(
            UUID owner, ConfigurationDtos.CategoryRequest request) {
        String canonicalProject = projectCode(owner, request.projectCode());
        String label = request.label().trim();
        String code = request.code() == null || request.code().isBlank()
                ? generatedCode(label, candidate -> repository
                        .existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(
                                owner, ConfigKind.CATEGORY, canonicalProject, candidate))
                : request.code().trim();

        if (repository.existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(
                owner, ConfigKind.CATEGORY, canonicalProject, code)) {
            throw new BadRequestException("Ya existe una categoría con ese código en este proyecto");
        }

        ConfigItem item = new ConfigItem();
        item.setOwnerId(owner);
        item.setKind(ConfigKind.CATEGORY);
        item.setCode(code);
        item.setLabel(label);
        item.setProjectCode(canonicalProject);
        item.setSortOrder(request.sortOrder());
        item.setActive(request.active());
        return categoryResponse(repository.save(item));
    }

    @Transactional
    public ConfigurationDtos.CategoryResponse patchCategory(
            UUID owner, UUID id, ConfigurationDtos.PatchRequest request) {
        ConfigItem item = repository.findByIdAndOwnerIdAndKindAndDeletedAtIsNull(
                        id, owner, ConfigKind.CATEGORY)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        if (request.label() != null) {
            if (request.label().isBlank()) {
                throw new BadRequestException("label cannot be blank");
            }
            item.setLabel(request.label().trim());
        }
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            item.setActive(request.active());
        }
        if (request.projectCode() != null
                && !request.projectCode().isBlank()
                && !request.projectCode().equalsIgnoreCase(item.getProjectCode())) {
            String nextProject = projectCode(owner, request.projectCode());
            if (repository.existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(
                    owner, ConfigKind.CATEGORY, nextProject, item.getCode())) {
                throw new BadRequestException("Ya existe una categoría con ese código en el proyecto de destino");
            }
            categoryUsage.moveRecords(owner, item.getCode(), item.getProjectCode(), nextProject);
            item.setProjectCode(nextProject);
        }
        return categoryResponse(item);
    }

    @Transactional
    public void deleteCategory(UUID owner, UUID id) {
        ConfigItem item = repository.findByIdAndOwnerIdAndKindAndDeletedAtIsNull(
                        id, owner, ConfigKind.CATEGORY)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        item.setDeletedAt(Instant.now());
    }

    public ConfigItem requireActiveCategory(UUID owner, String projectCode, String code) {
        String canonicalProject = projectCode(owner, projectCode);
        if (code == null || code.isBlank()) {
            throw new BadRequestException("categoryCode is required");
        }
        return repository.findByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(
                        owner, ConfigKind.CATEGORY, canonicalProject, code)
                .filter(ConfigItem::isActive)
                .orElseThrow(() -> new BadRequestException("Unknown or inactive categoryCode for this project"));
    }

    public void requireCategoryForUpdate(
            UUID owner,
            String currentProject,
            String currentCode,
            String nextProject,
            String nextCode) {
        if (currentProject != null
                && currentCode != null
                && currentProject.equalsIgnoreCase(nextProject)
                && currentCode.equalsIgnoreCase(nextCode)) {
            return;
        }
        requireActiveCategory(owner, nextProject, nextCode);
    }

    public static String categoryKey(String projectCode, String code) {
        return projectCode.toLowerCase(Locale.ROOT) + ":" + code.toLowerCase(Locale.ROOT);
    }

    @Transactional
    public ConfigurationDtos.ConfigOptionResponse createDayStatus(
            UUID owner, ConfigurationDtos.DayStatusRequest request) {
        requireCanonicalDayStatus(request.code());
        return create(
                owner,
                ConfigKind.DAY_STATUS,
                request.code(),
                request.label(),
                request.emoji(),
                request.sortOrder(),
                true,
                null);
    }

    @Transactional
    public ConfigurationDtos.ConfigOptionResponse createOption(
            UUID owner, ConfigKind kind, ConfigurationDtos.OptionRequest request) {
        return create(
                owner,
                kind,
                request.code(),
                request.label(),
                null,
                request.sortOrder(),
                request.active(),
                financeType(kind, request.financeType()));
    }

    @Transactional
    public ConfigurationDtos.ConfigOptionResponse patch(
            UUID owner, ConfigKind kind, String code, ConfigurationDtos.PatchRequest request) {
        ConfigItem item = find(owner, kind, code);

        if (kind == ConfigKind.DAY_STATUS && request.active() != null && !request.active()) {
            throw new BadRequestException("The day semaphore always requires three active colors");
        }
        if (kind == ConfigKind.FINANCE_ITEM
                && isTransfer(item)
                && ((request.active() != null && !request.active())
                        || (request.financeType() != null
                                && request.financeType() != FinanceItemType.TRANSFER))) {
            throw new BadRequestException("Transfer classification must remain available for investment accounts");
        }
        if (kind == ConfigKind.PROJECT
                && Boolean.FALSE.equals(request.active())
                && ("personal".equalsIgnoreCase(code) || projectUsage.hasActiveRecords(owner, code))) {
            throw new BadRequestException(
                    "Mové los registros antes de desactivar el proyecto. Personal debe permanecer activo.");
        }

        if (request.label() != null) {
            if (request.label().isBlank()) {
                throw new BadRequestException("label cannot be blank");
            }
            item.setLabel(request.label().trim());
        }
        if (request.emoji() != null) {
            item.setEmoji(request.emoji());
        }
        if (request.sortOrder() != null) {
            item.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            item.setActive(request.active());
        }
        if (request.financeType() != null) {
            item.setFinanceType(financeType(kind, request.financeType()));
        }
        return response(item);
    }

    @Transactional
    public void delete(UUID owner, ConfigKind kind, String code) {
        if (kind == ConfigKind.DAY_STATUS) {
            throw new BadRequestException("The day semaphore always requires three colors");
        }

        ConfigItem item = find(owner, kind, code);
        if (kind == ConfigKind.FINANCE_ITEM && isTransfer(item)) {
            throw new BadRequestException("Transfer classification is required for investment accounts");
        }
        if (kind == ConfigKind.PROJECT
                && ("personal".equalsIgnoreCase(code) || projectUsage.hasActiveRecords(owner, code))) {
            throw new BadRequestException(
                    "Mové los registros antes de eliminar el proyecto. Personal no se puede eliminar.");
        }
        item.setDeletedAt(Instant.now());
    }

    public String projectCode(UUID owner, String code) {
        return requireActive(owner, ConfigKind.PROJECT, code == null ? "personal" : code, "projectCode")
                .getCode();
    }

    public ConfigItem requireActive(UUID owner, ConfigKind kind, String code, String field) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)
                .filter(ConfigItem::isActive)
                .orElseThrow(() -> new BadRequestException("Unknown or inactive " + field));
    }

    public ConfigurationDtos.ConfigOptionResponse option(UUID owner, ConfigKind kind, String code) {
        return response(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)
                .orElseThrow(() -> new NotFoundException("Configuration option not found")));
    }

    private ConfigurationDtos.ConfigOptionResponse create(
            UUID owner,
            ConfigKind kind,
            String code,
            String label,
            String emoji,
            int sortOrder,
            boolean active,
            FinanceItemType financeType) {
        label = label.trim();
        code = code == null || code.isBlank()
                ? generatedCode(
                        label,
                        candidate -> repository.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(
                                owner, kind, candidate))
                : code.trim();

        if (repository.existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)) {
            throw new BadRequestException("Configuration code already exists");
        }

        ConfigItem item = new ConfigItem();
        item.setOwnerId(owner);
        item.setKind(kind);
        item.setCode(code);
        item.setLabel(label);
        item.setEmoji(emoji);
        item.setSortOrder(sortOrder);
        item.setActive(active);
        item.setFinanceType(financeType);
        return response(repository.save(item));
    }

    private ConfigItem find(UUID owner, ConfigKind kind, String code) {
        return repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, kind, code)
                .orElseThrow(() -> new NotFoundException("Configuration option not found"));
    }

    private void requireCanonicalDayStatus(String code) {
        if (code == null || !DAY_STATUS_CODES.contains(code.trim().toLowerCase())) {
            throw new BadRequestException("The day semaphore only supports green, yellow and red");
        }
    }

    private FinanceItemType financeType(ConfigKind kind, FinanceItemType type) {
        if (kind == ConfigKind.FINANCE_ITEM && type == null) {
            throw new BadRequestException("financeType is required");
        }
        if (kind != ConfigKind.FINANCE_ITEM && type != null) {
            throw new BadRequestException("financeType is only valid for finance items");
        }
        return type;
    }

    private String generatedCode(String label, Predicate<String> exists) {
        String base = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "opcion";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("_+$", "");
        }

        String candidate = base;
        int suffix = 2;
        while (exists.test(candidate)) {
            String tail = "_" + suffix++;
            candidate = base.substring(0, Math.min(base.length(), 80 - tail.length()))
                    .replaceAll("_+$", "")
                    + tail;
        }
        return candidate;
    }

    private boolean isTransfer(ConfigItem item) {
        return "transferencia".equalsIgnoreCase(item.getCode());
    }

    private ConfigurationDtos.ConfigOptionResponse response(ConfigItem item) {
        return new ConfigurationDtos.ConfigOptionResponse(
                item.getCode(),
                item.getLabel(),
                item.getEmoji(),
                item.getSortOrder(),
                item.isActive(),
                item.getFinanceType());
    }

    private ConfigurationDtos.CategoryResponse categoryResponse(ConfigItem item) {
        return new ConfigurationDtos.CategoryResponse(
                item.getId(),
                item.getCode(),
                item.getLabel(),
                item.getSortOrder(),
                item.isActive(),
                item.getProjectCode());
    }
}
