package com.accountposting.controller;

import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.UpdateLegRequestV2;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.GlobalExceptionHandler;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
@Import(GlobalExceptionHandler.class)
class AccountPostingLegControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AccountPostingLegServiceV2 service;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountPostingLegRequestV2 validLegRequest() {
        AccountPostingLegRequestV2 req = new AccountPostingLegRequestV2();
        req.setLegOrder(1);
        req.setTargetSystem("CBS");
        req.setAccount("ACC-001");
        return req;
    }

    private AccountPostingLegResponseV2 sampleLegResponse(Long legId, Long postingId) {
        AccountPostingLegResponseV2 resp = new AccountPostingLegResponseV2();
        resp.setPostingLegId(legId);
        resp.setPostingId(postingId);
        resp.setLegOrder(1);
        resp.setTargetSystem("CBS");
        resp.setStatus(LegStatus.SUCCESS);
        resp.setMode(LegMode.NORM);
        resp.setAttemptNumber(1);
        return resp;
    }

    // ── POST /v2/payment/account-posting/{postingId}/leg ──────────────────────

    @Nested
    class AddLeg {

        @Test
        void returns201_whenValidRequest() throws Exception {
            when(service.addLeg(eq(10L), any())).thenReturn(sampleLegResponse(100L, 10L));

            mockMvc.perform(post("/v2/payment/account-posting/10/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLegRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.posting_leg_id").value(100))
                    .andExpect(jsonPath("$.posting_id").value(10))
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }

        @Test
        void returns400_whenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/v2/payment/account-posting/10/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(2))))
                    .andExpect(jsonPath("$.errors[*].field",
                            hasItems("legOrder", "targetSystem", "account")));
        }

        @Test
        void returns400_whenLegOrderIsZero() throws Exception {
            AccountPostingLegRequestV2 req = validLegRequest();
            req.setLegOrder(0);

            mockMvc.perform(post("/v2/payment/account-posting/10/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='legOrder')]").exists());
        }

        @Test
        void returns400_whenTargetSystemBlank() throws Exception {
            AccountPostingLegRequestV2 req = validLegRequest();
            req.setTargetSystem("  ");

            mockMvc.perform(post("/v2/payment/account-posting/10/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='targetSystem')]").exists());
        }

        @Test
        void returns404_whenPostingNotFound() throws Exception {
            when(service.addLeg(eq(99L), any()))
                    .thenThrow(new ResourceNotFoundException("AccountPosting", 99L));

            mockMvc.perform(post("/v2/payment/account-posting/99/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLegRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.addLeg(any(), any())).thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(post("/v2/payment/account-posting/10/leg")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLegRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /v2/payment/account-posting/{postingId}/leg ───────────────────────

    @Nested
    class ListLegs {

        @Test
        void returns200_withLegList() throws Exception {
            when(service.listLegs(10L)).thenReturn(
                    List.of(sampleLegResponse(100L, 10L), sampleLegResponse(101L, 10L)));

            mockMvc.perform(get("/v2/payment/account-posting/10/leg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].posting_leg_id").value(100))
                    .andExpect(jsonPath("$[1].posting_leg_id").value(101));
        }

        @Test
        void returns200_withEmptyListWhenNoLegs() throws Exception {
            when(service.listLegs(10L)).thenReturn(List.of());

            mockMvc.perform(get("/v2/payment/account-posting/10/leg"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.listLegs(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/10/leg"))
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

            mockMvc.perform(get("/v2/payment/account-posting/10/leg/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posting_leg_id").value(100))
                    .andExpect(jsonPath("$.leg_order").value(1))
                    .andExpect(jsonPath("$.target_system").value("CBS"));
        }

        @Test
        void returns404_whenLegNotFound() throws Exception {
            when(service.getLeg(10L, 999L))
                    .thenThrow(new ResourceNotFoundException("AccountPostingLeg", 999L));

            mockMvc.perform(get("/v2/payment/account-posting/10/leg/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.getLeg(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/10/leg/100"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── PUT /v2/payment/account-posting/{postingId}/leg/{legId} ──────────────

    @Nested
    class UpdateLeg {

        @Test
        void returns200_whenValidRequest() throws Exception {
            UpdateLegRequestV2 req = new UpdateLegRequestV2();
            req.setStatus(LegStatus.SUCCESS);
            req.setReferenceId("REF-001");

            AccountPostingLegResponseV2 resp = sampleLegResponse(100L, 10L);
            resp.setReferenceId("REF-001");
            when(service.updateLeg(eq(10L), eq(100L), any())).thenReturn(resp);

            mockMvc.perform(put("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posting_leg_id").value(100))
                    .andExpect(jsonPath("$.reference_id").value("REF-001"));
        }

        @Test
        void returns400_whenStatusMissing() throws Exception {
            mockMvc.perform(put("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='status')]").exists());
        }

        @Test
        void returns404_whenLegNotFound() throws Exception {
            UpdateLegRequestV2 req = new UpdateLegRequestV2();
            req.setStatus(LegStatus.FAILED);
            when(service.updateLeg(eq(10L), eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("AccountPostingLeg", 999L));

            mockMvc.perform(put("/v2/payment/account-posting/10/leg/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns422_whenBusinessRuleViolated() throws Exception {
            UpdateLegRequestV2 req = new UpdateLegRequestV2();
            req.setStatus(LegStatus.SUCCESS);
            when(service.updateLeg(any(), any(), any()))
                    .thenThrow(new BusinessException("INVALID_TRANSITION",
                            "Cannot transition leg from FAILED to SUCCESS directly"));

            mockMvc.perform(put("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.name").value("INVALID_TRANSITION"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            UpdateLegRequestV2 req = new UpdateLegRequestV2();
            req.setStatus(LegStatus.SUCCESS);
            when(service.updateLeg(any(), any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(put("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
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
            when(service.manualUpdateLeg(eq(10L), eq(100L), eq(LegStatus.SUCCESS), isNull()))
                    .thenReturn(resp);

            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posting_leg_id").value(100))
                    .andExpect(jsonPath("$.mode").value("MANUAL"));
        }

        @Test
        void returns200_withStatusAndReason() throws Exception {
            AccountPostingLegResponseV2 resp = sampleLegResponse(100L, 10L);
            resp.setMode(LegMode.MANUAL);
            resp.setReason("Manually overridden after CBS timeout");
            when(service.manualUpdateLeg(eq(10L), eq(100L), eq(LegStatus.SUCCESS),
                    eq("Manually overridden after CBS timeout")))
                    .thenReturn(resp);

            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\", \"reason\": \"Manually overridden after CBS timeout\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posting_leg_id").value(100))
                    .andExpect(jsonPath("$.mode").value("MANUAL"))
                    .andExpect(jsonPath("$.reason").value("Manually overridden after CBS timeout"));
        }

        @Test
        void returns400_whenStatusIsNull() throws Exception {
            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[?(@.field=='status')]").exists());
        }

        @Test
        void returns400_whenBodyIsEmpty() throws Exception {
            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"));
        }

        @Test
        void returns404_whenLegNotFound() throws Exception {
            when(service.manualUpdateLeg(eq(10L), eq(999L), any(), any()))
                    .thenThrow(new ResourceNotFoundException("AccountPostingLeg", 999L));

            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.manualUpdateLeg(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(patch("/v2/payment/account-posting/10/leg/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SUCCESS\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
