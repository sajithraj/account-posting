package com.sajith.payments.redesign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.Amount;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import com.sajith.payments.redesign.dto.search.PostingSearchRequestV2;
import com.sajith.payments.redesign.dto.search.PostingSearchResponseV2;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.exception.BusinessException;
import com.sajith.payments.redesign.config.SecurityConfig;
import com.sajith.payments.redesign.exception.GlobalExceptionHandler;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.service.accountposting.AccountPostingServiceV2;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountPostingControllerV2.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@MockBean(JpaMetamodelMappingContext.class)
class AccountPostingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccountPostingServiceV2 service;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private IncomingPostingRequest validRequest() {
        IncomingPostingRequest req = new IncomingPostingRequest();
        req.setSourceRefId("SRC-001");
        req.setEndToEndRefId("E2E-001");
        req.setSourceName("IMX");
        req.setRequestType("IMX_CBS_GL");
        Amount amount = new Amount();
        amount.setValue("100.00");
        amount.setCurrency("USD");
        req.setAmount(amount);
        req.setCreditDebitIndicator("CREDIT");
        req.setDebtorAccount("ACC-DEBTOR-001");
        req.setCreditorAccount("ACC-CREDITOR-001");
        req.setRequestedExecutionDate("2026-03-21");
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

    // ── POST /v2/payment/account-posting/search ───────────────────────────────

    @Nested
    class Search {

        private PostingSearchResponseV2 searchResponse(AccountPostingFullResponseV2... items) {
            return PostingSearchResponseV2.builder()
                    .items(List.of(items))
                    .totalItems(items.length)
                    .offset(1)
                    .limit(20)
                    .build();
        }

        @Test
        void returns200_withPagedResults() throws Exception {
            when(service.search(any(PostingSearchRequestV2.class)))
                    .thenReturn(searchResponse(sampleResponse(1L), sampleResponse(2L)));

            mockMvc.perform(post("/v2/payment/account-posting/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(2)))
                    .andExpect(jsonPath("$.total_items").value(2));
        }

        @Test
        void returns200_withEmptyResults() throws Exception {
            when(service.search(any(PostingSearchRequestV2.class)))
                    .thenReturn(searchResponse());

            mockMvc.perform(post("/v2/payment/account-posting/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)))
                    .andExpect(jsonPath("$.total_items").value(0));
        }

        @Test
        void returns200_withNoBody() throws Exception {
            when(service.search(any(PostingSearchRequestV2.class)))
                    .thenReturn(searchResponse());

            mockMvc.perform(post("/v2/payment/account-posting/search")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.search(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/v2/payment/account-posting/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
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
            leg.setTransactionOrder(1);

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
                    .andExpect(jsonPath("$.responses[0].transaction_order").value(1));
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
            req.setRequestedBy("OPS-USER");

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
        void returns200_retryAllWhenNoPostingIds() throws Exception {
            RetryRequestV2 req = new RetryRequestV2();
            req.setRequestedBy("OPS-USER");

            RetryResponseV2 resp = RetryResponseV2.builder()
                    .totalPostings(3)
                    .successCount(2)
                    .failedCount(1)
                    .build();
            when(service.retry(any())).thenReturn(resp);

            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total_postings").value(3));
        }

        @Test
        void returns400_whenRequestedByMissing() throws Exception {
            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"posting_ids\": [1, 2]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='requestedBy')]").exists());
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            RetryRequestV2 req = new RetryRequestV2();
            req.setRequestedBy("OPS-USER");
            when(service.retry(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/v2/payment/account-posting/retry")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
