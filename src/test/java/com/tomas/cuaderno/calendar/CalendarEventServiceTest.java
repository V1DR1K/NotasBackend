package com.tomas.cuaderno.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tomas.cuaderno.common.errors.BadRequestException;

import com.tomas.cuaderno.configuration.ConfigurationDtos;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceTest {
    @Mock CalendarEventRepository repository;
    @Mock ConfigurationService configuration;
    @InjectMocks CalendarEventService service;

    @BeforeEach
    void useRequestedOrPersonalProject() {
        when(configuration.projectCode(any(UUID.class), nullable(String.class))).thenAnswer(invocation -> {
            String project = invocation.getArgument(1);
            return project == null ? "personal" : project;
        });
    }

    @Test
    void createEvent_whenCategoryIsActive_shouldPersistEvent() {
        UUID owner = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 31);
        var request = new CalendarEventDtos.CreateRequest(date, "Presentar el trabajo", "laburo", "laburo");
        var category = new ConfigurationDtos.ConfigOptionResponse("laburo", "Laburo", null, 0, true, null);
        when(configuration.categoriesIndexIncludingDeleted(owner)).thenReturn(Map.of("laburo:laburo", category));
        when(repository.save(any(CalendarEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CalendarEventDtos.Response response = service.create(owner, request);

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.description()).isEqualTo("Presentar el trabajo");
        assertThat(response.category().label()).isEqualTo("Laburo");
        verify(configuration).requireActiveCategory(owner, "laburo", "laburo");
        verify(repository).save(any(CalendarEvent.class));
    }

    @Test
    void createEvent_whenCategoryIsInactive_shouldRejectRequest() {
        UUID owner = UUID.randomUUID();
        when(configuration.requireActiveCategory(eq(owner), eq("personal"), eq("medico")))
                .thenThrow(new BadRequestException("Unknown or inactive categoryCode"));

        assertThatThrownBy(() -> service.create(owner, new CalendarEventDtos.CreateRequest(
                LocalDate.of(2026, 8, 31), "Turno", "medico", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inactive");
        verify(repository, never()).save(any(CalendarEvent.class));
    }
}
