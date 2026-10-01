package com.tomas.cuaderno.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@ExtendWith(MockitoExtension.class)
class ProjectConfigurationServiceTest {
    @Mock ConfigItemRepository repository;
    @Mock ProjectUsageRepository usage;
    @Mock CategoryUsageRepository categoryUsage;
    @InjectMocks ConfigurationService service;

    @Test
    void projectCode_whenUnspecified_shouldUseActivePersonalProject() {
        UUID owner = UUID.randomUUID();
        ConfigItem personal = new ConfigItem();
        personal.setCode("personal");
        personal.setActive(true);
        when(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.PROJECT, "personal"))
                .thenReturn(Optional.of(personal));

        assertThat(service.projectCode(owner, null)).isEqualTo("personal");
    }

    @Test
    void deleteProject_whenItHasRecords_shouldPreserveConfiguration() {
        UUID owner = UUID.randomUUID();
        ConfigItem project = new ConfigItem();
        project.setCode("facultad");
        when(repository.findByOwnerIdAndKindAndCodeIgnoreCaseAndDeletedAtIsNull(owner, ConfigKind.PROJECT, "facultad"))
                .thenReturn(Optional.of(project));
        when(usage.hasActiveRecords(owner, "facultad")).thenReturn(true);

        assertThatThrownBy(() -> service.delete(owner, ConfigKind.PROJECT, "facultad"))
                .isInstanceOf(BadRequestException.class);
        assertThat(project.getDeletedAt()).isNull();
        verify(usage).hasActiveRecords(owner, "facultad");
    }
}
