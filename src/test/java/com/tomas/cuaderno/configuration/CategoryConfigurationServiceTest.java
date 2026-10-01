package com.tomas.cuaderno.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.errors.BadRequestException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CategoryConfigurationServiceTest {
    @Mock ConfigItemRepository repository;
    @Mock ProjectUsageRepository projectUsage;
    @Mock CategoryUsageRepository categoryUsage;
    @InjectMocks ConfigurationService service;

    @Test
    void requireActiveCategoryRejectsCategoryFromAnotherProject() {
        UUID owner = UUID.randomUUID();
        ConfigItem project = new ConfigItem();
        project.setCode("facultad");
        when(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.PROJECT, "facultad"))
                .thenReturn(Optional.of(project));
        when(repository.findByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.CATEGORY, "facultad", "work"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveCategory(owner, "facultad", "work"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("categoryCode");
    }

    @Test
    void movingCategoryMovesAllLinkedRecordsBeforeChangingItsProject() {
        UUID owner = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ConfigItem category = new ConfigItem();
        category.setOwnerId(owner);
        category.setKind(ConfigKind.CATEGORY);
        category.setCode("work");
        category.setLabel("Trabajo");
        category.setProjectCode("personal");
        when(repository.findByIdAndOwnerIdAndKindAndDeletedAtIsNull(categoryId, owner, ConfigKind.CATEGORY)).thenReturn(Optional.of(category));
        when(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.PROJECT, "laburo"))
                .thenReturn(Optional.of(project("laburo")));
        when(repository.existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.CATEGORY, "laburo", "work"))
                .thenReturn(false);

        var response = service.patchCategory(owner, categoryId, new ConfigurationDtos.PatchRequest(null, null, null, null, null, "laburo"));

        assertThat(response.projectCode()).isEqualTo("laburo");
        assertThat(response.code()).isEqualTo("work");
        verify(categoryUsage).moveRecords(owner, "work", "personal", "laburo");
        assertThat(ConfigurationService.class.getMethod("patchCategory", UUID.class, UUID.class, ConfigurationDtos.PatchRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void movingCategoryIntoAnExistingCodeKeepsBothCategoriesUnchanged() {
        UUID owner = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        ConfigItem category = new ConfigItem();
        category.setOwnerId(owner);
        category.setKind(ConfigKind.CATEGORY);
        category.setCode("work");
        category.setLabel("Trabajo");
        category.setProjectCode("personal");
        when(repository.findByIdAndOwnerIdAndKindAndDeletedAtIsNull(categoryId, owner, ConfigKind.CATEGORY)).thenReturn(Optional.of(category));
        when(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.PROJECT, "laburo"))
                .thenReturn(Optional.of(project("laburo")));
        when(repository.existsByOwnerIdAndKindAndProjectCodeIgnoreCaseAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.CATEGORY, "laburo", "work"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.patchCategory(owner, categoryId, new ConfigurationDtos.PatchRequest(null, null, null, null, null, "laburo")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("destino");
        assertThat(category.getProjectCode()).isEqualTo("personal");
        verify(categoryUsage, never()).moveRecords(owner, "work", "personal", "laburo");
    }

    private ConfigItem project(String code) {
        ConfigItem item = new ConfigItem();
        item.setCode(code);
        item.setActive(true);
        return item;
    }
}
