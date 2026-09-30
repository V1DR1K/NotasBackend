package com.tomas.cuaderno.finance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.tomas.cuaderno.common.errors.GlobalExceptionHandler;
import com.tomas.cuaderno.common.pagination.PageResponse;
import com.tomas.cuaderno.common.security.AppPrincipal;
import com.tomas.cuaderno.configuration.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FinanceControllerTest {
    @Mock FinanceService service;
    @Mock FinanceAccountService accounts;
    @Mock ExchangeRateService rates;
    @Mock CryptoInvestmentService crypto;
    MockMvc mvc;
    UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(new AppPrincipal(owner, "test", null, "USER", true), null));
        mvc = MockMvcBuilders.standaloneSetup(new FinanceController(service, accounts, rates, crypto))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void createsExplicitTransferWithAdditiveResponseFields() throws Exception {
        when(service.transfer(eq(owner), any())).thenReturn(response());
        mvc.perform(post("/api/finance/transfers").contentType(MediaType.APPLICATION_JSON).content("""
                {"sourceAccountCode":"mercadopago","destinationAccountCode":"inversiones_pesos","date":"2026-09-30","amountArs":30000,"note":"aporte"}
                """))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.movementType").value("TRANSFER"))
                .andExpect(jsonPath("$.sourceAccountCode").value("mercadopago"))
                .andExpect(jsonPath("$.destinationAccountCode").value("inversiones_pesos"))
                .andExpect(jsonPath("$.bucket").value("INCOME"));
        verify(service).transfer(owner, new FinanceDtos.TransferRequest("mercadopago", "inversiones_pesos", LocalDate.of(2026, 9, 30), new BigDecimal("30000"), "aporte"));
    }
    @Test void patchAcceptsPartialTransferRequest() throws Exception {
        when(service.patchTransfer(eq(owner), eq(id), any())).thenReturn(response());
        mvc.perform(patch("/api/finance/transfers/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"amountArs\":25000}"))
                .andExpect(status().isOk());
        verify(service).patchTransfer(owner, id, new FinanceDtos.TransferPatchRequest(null, null, null, new BigDecimal("25000"), null));
    }
    @Test void rejectsMissingRouteAndNonPositiveAmountsBeforeCallingService() throws Exception {
        mvc.perform(post("/api/finance/transfers").contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-30\",\"amountArs\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void passesMovementTypeFilterAndPreservesPaginationContract() throws Exception {
        when(service.list(eq(owner), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class), eq(FinanceMovementType.TRANSFER)))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true, true));
        mvc.perform(get("/api/finance/movements").param("movementType", "TRANSFER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].movementType").value("TRANSFER"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    private FinanceDtos.Response response() {
        return new FinanceDtos.Response(id, LocalDate.of(2026, 9, 30), FinanceBucket.INCOME, "inversiones_pesos",
                new FinanceDtos.MoneyResponse(new BigDecimal("30000"), new BigDecimal("30"), new BigDecimal("1000")),
                new ConfigurationDtos.ConfigOptionResponse("transferencia", "Transferencia", null, 0, true, FinanceItemType.TRANSFER),
                null, null, null, FinanceMovementType.TRANSFER, "mercadopago", "inversiones_pesos");
    }
}
