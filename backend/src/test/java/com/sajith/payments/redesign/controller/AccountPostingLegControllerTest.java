package com.sajith.payments.redesign.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.exception.BusinessException;
import com.sajith.payments.redesign.config.SecurityConfig;
import com.sajith.payments.redesign.exception.GlobalExceptionHandler;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountPostingLegControllerV2.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
@MockBean(JpaMetamodelMappingContext.class)
class AccountPostingLegControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccountPostingLegServiceV2 service;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountPostingLegResponseV2 sampleLegResponse(Long legId, Long postingId) {
        AccountPostingLegResponseV2 resp = new AccountPostingLegResponseV2();
        resp.setTransactionId(legId);
        resp.setPostingId(postingId);
        resp.setTransactionOrder(1);
        resp.setTargetSystem("CBS");
        resp.setStatus(LegStatus.SUCCESS);
        resp.setMode(LegMode.NORM);
        resp.setAttemptNumber(1);
        return resp;
    }

    // ── GET /v2/payment/account-posting/{postingId}/leg ───────────────────────

    @Nested
    class ListLegs {

        @Test
        void returns200_withLegList() throws Exception {
            when(service.listLegs(10L)).thenReturn(
                    List.of(sampleLegResponse(100L, 10L), sampleLegResponse(101L, 10L)));

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].transaction_id").value(100))
                    .andExpect(jsonPath("$[1].transaction_id").value(101));
        }

        @Test
        void returns200_withEmptyListWhenNoLegs() throws Exception {
            when(service.listLegs(10L)).thenReturn(List.of());

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.listLegs(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /v2/payment/account-posting/{postingId}/leg/{legId} ──────────────

    @Nested
    class GetLeg {

        @Test
        void returns200_whenLegExists() throws Exception {
            when(service.getLeg(10L, 100L)).thenReturn(sampleLegResponse(100L, 10L));

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value(100))
                    .andExpect(jsonPath("$.transaction_order").value(1))
                    .andExpect(jsonPath("$.target_system").value("CBS"));
        }

        @Test
        void returns404_whenLegNotFound() throws Exception {
            when(service.getLeg(10L, 999L))
                    .thenThrow(new ResourceNotFoundException("AccountPostingLeg", 999L));

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.getLeg(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/10/transaction/100"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── PATCH /v2/payment/account-posting/{postingId}/leg/{legId} ────────────

    @Nested
    class ManualUpdateLeg {

        @Test
        void returns200_whenValidStatus() throws Exception {
            AccountPostingLegResponseV2 resp = sampleLegResponse(100L, 10L);
            resp.setMode(LegMode.MANUAL);
            when(service.manualUpdateLeg(eq(10L), eq(100L), any()))
                    .thenReturn(resp);

            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\", \"requested_by\": \"OPS-USER\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value(100))
                    .andExpect(jsonPath("$.mode").value("MANUAL"));
        }

        @Test
        void returns200_withStatusAndReason() throws Exception {
            AccountPostingLegResponseV2 resp = sampleLegResponse(100L, 10L);
            resp.setMode(LegMode.MANUAL);
            resp.setReason("Manually overridden after CBS timeout");
            when(service.manualUpdateLeg(eq(10L), eq(100L), any()))
                    .thenReturn(resp);

            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\", \"reason\": \"Manually overridden after CBS timeout\", \"requested_by\": \"OPS-USER\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value(100))
                    .andExpect(jsonPath("$.mode").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value("Manually overridden after CBS timeout"));
        }

        @Test
        void returns400_whenStatusIsNull() throws Exception {
            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": null, \"requested_by\": \"OPS-USER\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='status')]").exists());
        }

        @Test
        void returns400_whenBodyIsEmpty() throws Exception {
            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"));
        }

        @Test
        void returns400_whenRequestedByMissing() throws Exception {
            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='requestedBy')]").exists());
        }

        @Test
        void returns404_whenLegNotFound() throws Exception {
            when(service.manualUpdateLeg(eq(10L), eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("AccountPostingLeg", 999L));

            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\", \"requested_by\": \"OPS-USER\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.manualUpdateLeg(any(), any(), any()))
                    .thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(patch("/v2/payment/account-posting/10/transaction/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\", \"requested_by\": \"OPS-USER\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
