package com.accountposting.controller;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingFullResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountposting.AccountPostingSearchRequestV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.dto.retry.RetryRequestV2;
import com.accountposting.dto.retry.RetryResponseV2;
import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.GlobalExceptionHandler;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.service.accountposting.AccountPostingServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(AccountPostingControllerV2.class)
@Import(GlobalExceptionHandler.class)
class AccountPostingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccountPostingServiceV2 service;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountPostingRequestV2 validRequest() {
        AccountPostingRequestV2 req = new AccountPostingRequestV2();
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

    private AccountPostingCreateResponseV2 sampleCreateResponse() {
        return AccountPostingCreateResponseV2.builder()
                .sourceReferenceId("SRC-001")
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.ACSP)
                .build();
    }

    private AccountPostingFullResponseV2 sampleResponse(Long id) {
        return AccountPostingFullResponseV2.builder()
                .postingId(id)
                .sourceReferenceId("SRC-001")
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.ACSP)
                .build();
    }

    // ── POST /v2/payment/account-posting ──────────────────────────────────────

    @Nested
    class Create {

        @Test
        void returns201_whenValidRequest() throws Exception {
            when(service.create(any())).thenReturn(sampleCreateResponse());

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.source_reference_id").value("SRC-001"))
                    .andExpect(jsonPath("$.end_to_end_reference_id").value("E2E-001"))
                    .andExpect(jsonPath("$.posting_status").value("ACSP"));
        }

        @Test
        void returns400_whenAllRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/v2/payment/account-posting")
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
            AccountPostingRequestV2 req = validRequest();
            req.setSourceReferenceId("   ");

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceReferenceId')]").exists());
        }

        @Test
        void returns400_whenAmountIsZero() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setAmount(BigDecimal.ZERO);

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
        }

        @Test
        void returns400_whenAmountIsNegative() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setAmount(new BigDecimal("-10.00"));

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='amount')]").exists());
        }

        @Test
        void returns400_whenCurrencyIsWrongLength() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setCurrency("US");

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='currency')]").exists());
        }

        @Test
        void returns400_whenSourceNameMissing() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setSourceName(null);

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceName')]").exists());
        }

        @Test
        void returns400_whenRequestTypeMissing() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setRequestType(null);

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='requestType')]").exists());
        }

        @Test
        void returns400_whenCreditDebitIndicatorMissing() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setCreditDebitIndicator(null);

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='creditDebitIndicator')]").exists());
        }

        @Test
        void returns400_whenRequestedExecutionDateMissing() throws Exception {
            AccountPostingRequestV2 req = validRequest();
            req.setRequestedExecutionDate(null);

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='requestedExecutionDate')]").exists());
        }

        @Test
        void returns400_whenDuplicateEndToEndReferenceId() throws Exception {
            when(service.create(any()))
                    .thenThrow(new BusinessException("DUPLICATE_E2E_REF",
                            "Posting with this endToEndReferenceId already exists"));

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("DUPLICATE_E2E_REF"))
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        void returns422_whenNoConfigFound() throws Exception {
            when(service.create(any()))
                    .thenThrow(new BusinessException("NO_CONFIG_FOUND",
                            "No posting config found for requestType: PAYMENT"));

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.name").value("NO_CONFIG_FOUND"));
        }

        @Test
        void returns500_whenServiceThrowsUnexpectedException() throws Exception {
            when(service.create(any())).thenThrow(new RuntimeException("DB connection lost"));

            mockMvc.perform(post("/v2/payment/account-posting")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
        }
    }

    // ── GET /v2/payment/account-posting ───────────────────────────────────────

    @Nested
    class Search {

        @Test
        void returns200_withPagedResults() throws Exception {
            Page<AccountPostingFullResponseV2> page = new PageImpl<>(
                    List.of(sampleResponse(1L), sampleResponse(2L)));
            when(service.search(any(AccountPostingSearchRequestV2.class), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/v2/payment/account-posting"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.total_elements").value(2));
        }

        @Test
        void returns200_withEmptyResults() throws Exception {
            when(service.search(any(), any())).thenReturn(Page.empty());

            mockMvc.perform(get("/v2/payment/account-posting"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }

        @Test
        void returns200_withSearchCriteriaParams() throws Exception {
            when(service.search(any(), any())).thenReturn(Page.empty());

            mockMvc.perform(get("/v2/payment/account-posting")
                            .param("postingStatus", "ACSP")
                            .param("sourceName", "TEST-SOURCE")
                            .param("requestType", "PAYMENT"))
                    .andExpect(status().isOk());

            verify(service).search(any(), any());
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.search(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /v2/payment/account-posting/{postingId} ───────────────────────────

    @Nested
    class FindById {

        @Test
        void returns200_whenPostingExists() throws Exception {
            LegResponseV2 leg = new LegResponseV2();
            leg.setName("CBS");
            leg.setType("POSTING");
            leg.setLegOrder(1);

            AccountPostingFullResponseV2 resp = AccountPostingFullResponseV2.builder()
                    .postingId(42L)
                    .postingStatus(PostingStatus.ACSP)
                    .responses(List.of(leg))
                    .build();
            when(service.findById(42L)).thenReturn(resp);

            mockMvc.perform(get("/v2/payment/account-posting/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posting_id").value(42))
                    .andExpect(jsonPath("$.responses", hasSize(1)))
                    .andExpect(jsonPath("$.responses[0].name").value("CBS"))
                    .andExpect(jsonPath("$.responses[0].leg_order").value(1));
        }

        @Test
        void returns404_whenPostingNotFound() throws Exception {
            when(service.findById(99L))
                    .thenThrow(new ResourceNotFoundException("AccountPosting", 99L));

            mockMvc.perform(get("/v2/payment/account-posting/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("99")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.findById(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /v2/payment/account-posting/retry ────────────────────────────────

    @Nested
    class Retry {

        @Test
        void returns200_withExplicitPostingIds() throws Exception {
            RetryRequestV2 req = new RetryRequestV2();
            req.setPostingIds(List.of(1L, 2L));

            RetryResponseV2 resp = RetryResponseV2.builder()
                    .totalPostings(2)
                    .successCount(2)
                    .failedCount(0)
                    .build();
            when(service.retry(any())).thenReturn(resp);

            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_postings").value(2))
                    .andExpect(jsonPath("$.success_count").value(2))
                    .andExpect(jsonPath("$.failed_count").value(0));
        }

        @Test
        void returns200_withEmptyBodyRetriesAll() throws Exception {
            RetryResponseV2 resp = RetryResponseV2.builder()
                    .totalPostings(3)
                    .successCount(2)
                    .failedCount(1)
                    .build();
            when(service.retry(any())).thenReturn(resp);

            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_postings").value(3));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.retry(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
