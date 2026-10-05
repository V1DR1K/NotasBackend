package com.tomas.cuaderno.repositories;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.List;
import com.tomas.cuaderno.repositories.DatabaseManagerDtos.DatabaseTarget;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import com.tomas.cuaderno.common.security.SecurityConfig;
import com.tomas.cuaderno.common.security.JwtAuthenticationFilter;
import com.tomas.cuaderno.common.security.RateLimitFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = RepositoryDatabaseController.class, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class)
})
@Import(RepositoryDatabaseControllerSecurityTest.MethodSecurity.class)
class RepositoryDatabaseControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DatabaseManagerService databases;
    @MockBean RepositoryBackupClient backups;

    @Test void refusesNonAdminEvenWhenTheyCallTheApiDirectly() throws Exception {
        mvc.perform(get("/api/repositories/databases").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("person").roles("USER")))
                .andExpect(status().isForbidden());
    }
    @Test void allowsAdminsToListFixedDatabaseTargets() throws Exception {
        when(databases.targets()).thenReturn(List.of(new DatabaseTarget("notes","Notes")));
        mvc.perform(get("/api/repositories/databases").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("operator").roles("ADMIN")))
                .andExpect(status().isOk());
    }
    @EnableMethodSecurity
    static class MethodSecurity {}
}
