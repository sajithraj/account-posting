package com.accountposting.controller;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountposting.AccountPostingSearchRequest;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.GlobalExceptionHandler;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.service.AccountPostingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountPostingController.class)
@Import(GlobalExceptionHandler.class)
class AccountPostingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountPostingService service;

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountPostingRequest validRequest() {
        AccountPostingRequest req = new AccountPostingRequest();
        req.setSourceReferenceId("SRC-001");
        req.setEndToEndReferenceId("E2E-001");
        req.setSourceName("IMX");
        req.setRequestType("IMX_CBS_GL");
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("USD");
        req.setCreditDebitIndicator(CreditDebitIndicator.CREDIT);
        req.setDebtorAccount("ACC-DEBTOR-001");
        req.setCreditorAccount("ACC-CREDITOR-001");
        req.setRequestedExecutionDate(LocalDate.of(2026, 3, 21));
        return req;
    }

    private AccountPostingCreateResponse sampleCreateResponse() {
        return AccountPostingCreateResponse.builder()
                .sourceReferenceId("SRC-001")
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.SUCCESS)
                .build();
    }

    private AccountPostingResponse sampleResponse(Long id) {
        return AccountPostingResponse.builder()
                .postingId(id)
                .sourceReferenceId("SRC-001")
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.SUCCESS)
                .build();
    }

    // ── POST /account-posting ──────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void returns201_whenValidRequest() throws Exception {
            when(service.create(any())).thenReturn(sampleCreateResponse());

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sourceReferenceId").value("SRC-001"))
                    .andExpect(jsonPath("$.endToEndReferenceId").value("E2E-001"))
                    .andExpect(jsonPath("$.postingStatus").value("SUCCESS"));
        }

        @Test
        void returns400_whenAllRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(5))))
                    .andExpect(jsonPath("$.errors[*].field",
                            hasItems("sourceReferenceId", "endToEndReferenceId", "amount",
                                    "currency", "requestedExecutionDate")));
        }

        @Test
        void returns400_whenSourceReferenceIdBlank() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setSourceReferenceId("   ");

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceReferenceId')]").exists());
        }

        @Test
        void returns400_whenAmountIsZero() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setAmount(BigDecimal.ZERO);

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
        }

        @Test
        void returns400_whenAmountIsNegative() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setAmount(new BigDecimal("-10.00"));

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
        }

        @Test
        void returns400_whenCurrencyIsWrongLength() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setCurrency("US");

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='currency')]").exists());
        }

        @Test
        void returns400_whenSourceNameMissing() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setSourceName(null);

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceName')]").exists());
        }

        @Test
        void returns400_whenRequestTypeMissing() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setRequestType(null);

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='requestType')]").exists());
        }

        @Test
        void returns400_whenCreditDebitIndicatorMissing() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setCreditDebitIndicator(null);

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='creditDebitIndicator')]").exists());
        }

        @Test
        void returns400_whenRequestedExecutionDateMissing() throws Exception {
            AccountPostingRequest req = validRequest();
            req.setRequestedExecutionDate(null);

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='requestedExecutionDate')]").exists());
        }

        @Test
        void returns422_whenDuplicateEndToEndReferenceId() throws Exception {
            when(service.create(any()))
                    .thenThrow(new BusinessException("DUPLICATE_E2E_REF",
                            "Posting with this endToEndReferenceId already exists"));

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.name").value("DUPLICATE_E2E_REF"))
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        void returns422_whenNoConfigFound() throws Exception {
            when(service.create(any()))
                    .thenThrow(new BusinessException("NO_CONFIG_FOUND",
                            "No posting config found for requestType: PAYMENT"));

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.name").value("NO_CONFIG_FOUND"));
        }

        @Test
        void returns500_whenServiceThrowsUnexpectedException() throws Exception {
            when(service.create(any())).thenThrow(new RuntimeException("DB connection lost"));

            mockMvc.perform(post("/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
    }

    // ── GET /account-posting ───────────────────────────────────────────────────

    @Nested
    class Search {

        @Test
        void returns200_withPagedResults() throws Exception {
            Page<AccountPostingResponse> page = new PageImpl<>(
                    List.of(sampleResponse(1L), sampleResponse(2L)));
            when(service.search(any(AccountPostingSearchRequest.class), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/account-posting"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void returns200_withEmptyResults() throws Exception {
            when(service.search(any(), any())).thenReturn(Page.empty());

            mockMvc.perform(get("/account-posting"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void returns200_withSearchCriteriaParams() throws Exception {
            when(service.search(any(), any())).thenReturn(Page.empty());

            mockMvc.perform(get("/account-posting")
                            .param("postingStatus", "SUCCESS")
                            .param("sourceName", "TEST-SOURCE")
                            .param("requestType", "PAYMENT"))
                    .andExpect(status().isOk());

            verify(service).search(any(), any());
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.search(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/account-posting"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /account-posting/{postingId} ──────────────────────────────────────

    @Nested
    class FindById {

        @Test
        void returns200_whenPostingExists() throws Exception {
            LegResponse leg = new LegResponse();
            leg.setName("CBS");
            leg.setType("POSTING");
            leg.setLegOrder(1);

            AccountPostingResponse resp = AccountPostingResponse.builder()
                    .postingId(42L)
                    .postingStatus(PostingStatus.SUCCESS)
                    .responses(List.of(leg))
                    .build();
            when(service.findById(42L)).thenReturn(resp);

            mockMvc.perform(get("/account-posting/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.postingId").value(42))
                    .andExpect(jsonPath("$.responses", hasSize(1)))
                    .andExpect(jsonPath("$.responses[0].name").value("CBS"))
                    .andExpect(jsonPath("$.responses[0].legOrder").value(1));
        }

        @Test
        void returns404_whenPostingNotFound() throws Exception {
            when(service.findById(99L))
                    .thenThrow(new ResourceNotFoundException("AccountPosting", 99L));

            mockMvc.perform(get("/account-posting/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("99")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.findById(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/account-posting/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /account-posting/retry ────────────────────────────────────────────

    @Nested
    class Retry {

        @Test
        void returns200_withExplicitPostingIds() throws Exception {
            RetryRequest req = new RetryRequest();
            req.setPostingIds(List.of(1L, 2L));

            RetryResponse resp = RetryResponse.builder()
                    .totalLegsRetried(2)
                    .successCount(2)
                    .failedCount(0)
                    .build();
            when(service.retry(any())).thenReturn(resp);

            mockMvc.perform(post("/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalLegsRetried").value(2))
                    .andExpect(jsonPath("$.successCount").value(2))
                    .andExpect(jsonPath("$.failedCount").value(0));
        }

        @Test
        void returns200_withEmptyBodyRetriesAll() throws Exception {
            RetryResponse resp = RetryResponse.builder()
                    .totalLegsRetried(3)
                    .successCount(2)
                    .failedCount(1)
                    .build();
            when(service.retry(any())).thenReturn(resp);

            // No body — retries all non-SUCCESS postings
            mockMvc.perform(post("/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalLegsRetried").value(3));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.retry(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

}
