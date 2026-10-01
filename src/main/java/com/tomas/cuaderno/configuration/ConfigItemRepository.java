package com.tomas.cuaderno.configuration;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConfigItemRepository extends JpaRepository<ConfigItem, UUID> {
    List<ConfigItem> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    List<ConfigItem> findByOwnerIdAndKindAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(UUID ownerId, ConfigKind kind);
    List<ConfigItem> findByOwnerIdAndKindAndProjectCodeIgnoreCaseAndDeletedAtIsNullOrderBySortOrderAscCodeAsc(UUID ownerId, ConfigKind kind, String projectCode);
    List<ConfigItem> findByOwnerIdAndKindOrderBySortOrderAscCodeAsc(UUID ownerId, ConfigKind kind);
    Optional<ConfigItem> findByIdAndOwnerIdAndKindAndDeletedAtIsNull(UUID id, UUID ownerId, ConfigKind kind);
    Optional<ConfigItem> findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(UUID ownerId, ConfigKind kind, String code);
    boolean existsByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(UUID ownerId, ConfigKind kind, String code);
    boolean existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(UUID ownerId, ConfigKind kind, String projectCode, String code);
    Optional<ConfigItem> findByIdAndOwnerIdAndKind(UUID id, UUID ownerId, ConfigKind kind);
}
