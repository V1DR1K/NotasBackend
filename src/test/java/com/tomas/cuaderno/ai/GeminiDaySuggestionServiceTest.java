package com.tomas.cuaderno.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tomas.cuaderno.configuration.ConfigurationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeminiDaySuggestionServiceTest {
    @Test
    void leavesAnalysisPendingWithoutExposingAnApiCallWhenKeyIsMissing() {
        GeminiProperties properties = new GeminiProperties();
        ConfigurationService configuration = mock(ConfigurationService.class);
        GeminiDaySuggestionService service = new GeminiDaySuggestionService(properties, configuration, new ObjectMapper());

        GeminiDaySuggestionService.Suggestion result = service.suggest(UUID.randomUUID(), "Hoy fue un día tranquilo");

        assertThat(result.analyzed()).isFalse();
        assertThat(result.statusCode()).isEmpty();
        assertThat(result.feelingCodes()).isEmpty();
        verifyNoInteractions(configuration);
    }
}
