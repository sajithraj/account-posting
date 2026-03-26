package com.sajith.payments.redesign.controller;

import com.sajith.payments.redesign.dto.config.PostingConfigRequestV2;
import com.sajith.payments.redesign.dto.config.PostingConfigResponseV2;
import com.sajith.payments.redesign.exception.GlobalExceptionHandler;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.service.config.PostingConfigServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostingConfigControllerV2.class)
@Import(GlobalExceptionHandler.class)
class PostingConfigControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PostingConfigServiceV2 postingConfigService;

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PostingConfigRequestV2 validRequest() {
        PostingConfigRequestV2 req = new PostingConfigRequestV2();
        req.setSourceName("DMS");
        req.setRequestType("CBS_GL");
        req.setTargetSystem("CBS");
        req.setOperation("POSTING");
        req.setOrderSeq(1);
        return req;
    }

    private PostingConfigResponseV2 sampleResponse(Long configId) {
        PostingConfigResponseV2 resp = new PostingConfigResponseV2();
        resp.setConfigId(configId);
        resp.setSourceName("DMS");
        resp.setRequestType("CBS_GL");
        resp.setTargetSystem("CBS");
        resp.setOperation("POSTING");
        resp.setOrderSeq(1);
        return resp;
    }

    // ── GET /v2/payment/account-posting/config ─────────────────────────────────

    @Nested
    class GetAll {

        @Test
        void returns200_withAllConfigs() throws Exception {
            when(postingConfigService.getAll()).thenReturn(
                    List.of(sampleResponse(1L), sampleResponse(2L)));

            mockMvc.perform(get("/v2/payment/account-posting/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].config_id").value(1))
                    .andExpect(jsonPath("$[0].request_type").value("CBS_GL"));
        }

        @Test
        void returns200_withEmptyList() throws Exception {
            when(postingConfigService.getAll()).thenReturn(List.of());

            mockMvc.perform(get("/v2/payment/account-posting/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(postingConfigService.getAll()).thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/v2/payment/account-posting/config"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /v2/payment/account-posting/config/{requestType} ──────────────────

    @Nested
    class GetByRequestType {

        @Test
        void returns200_withMatchingConfigs() throws Exception {
            when(postingConfigService.getByRequestType("CBS_GL"))
                    .thenReturn(List.of(sampleResponse(1L), sampleResponse(2L)));

            mockMvc.perform(get("/v2/payment/account-posting/config/CBS_GL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void returns200_withEmptyListForUnknownType() throws Exception {
            when(postingConfigService.getByRequestType("UNKNOWN")).thenReturn(List.of());

            mockMvc.perform(get("/v2/payment/account-posting/config/UNKNOWN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(postingConfigService.getByRequestType(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/v2/payment/account-posting/config/CBS_GL"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /v2/payment/account-posting/config ────────────────────────────────

    @Nested
    class Create {

        @Test
        void returns201_whenValidRequest() throws Exception {
            when(postingConfigService.create(any())).thenReturn(sampleResponse(10L));

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.config_id").value(10))
                    .andExpect(jsonPath("$.target_system").value("CBS"))
                    .andExpect(jsonPath("$.order_seq").value(1));
        }

        @Test
        void returns400_whenAllRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field",
                            hasItems("sourceName", "requestType", "targetSystem",
                                    "operation", "orderSeq")));
        }

        @Test
        void returns400_whenSourceNameBlank() throws Exception {
            PostingConfigRequestV2 req = validRequest();
            req.setSourceName("  ");

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceName')]").exists());
        }

        @Test
        void returns400_whenOrderSeqIsZero() throws Exception {
            PostingConfigRequestV2 req = validRequest();
            req.setOrderSeq(0);

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='orderSeq')]").exists());
        }

        @Test
        void returns400_whenOrderSeqIsNegative() throws Exception {
            PostingConfigRequestV2 req = validRequest();
            req.setOrderSeq(-1);

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='orderSeq')]").exists());
        }

        @Test
        void returns400_whenDuplicateConfigOrder() throws Exception {
            when(postingConfigService.create(any())).thenThrow(
                    new DataIntegrityViolationException(
                            "could not execute statement [uq_posting_config_request_type_order]"));

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("DUPLICATE_CONFIG_ORDER"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(postingConfigService.create(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/v2/payment/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── PUT /v2/payment/account-posting/config/{configId} ──────────────────────

    @Nested
    class Update {

        @Test
        void returns200_whenValidRequest() throws Exception {
            PostingConfigResponseV2 updated = sampleResponse(5L);
            updated.setOrderSeq(2);
            when(postingConfigService.update(eq(5L), any())).thenReturn(updated);

            PostingConfigRequestV2 req = validRequest();
            req.setOrderSeq(2);

            mockMvc.perform(put("/v2/payment/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.config_id").value(5))
                    .andExpect(jsonPath("$.order_seq").value(2));
        }

        @Test
        void returns400_whenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(put("/v2/payment/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"));
        }

        @Test
        void returns404_whenConfigNotFound() throws Exception {
            when(postingConfigService.update(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("PostingConfig", 999L));

            mockMvc.perform(put("/v2/payment/account-posting/config/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(postingConfigService.update(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(put("/v2/payment/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── DELETE /v2/payment/account-posting/config/{configId} ───────────────────

    @Nested
    class Delete {

        @Test
        void returns204_whenDeleted() throws Exception {
            doNothing().when(postingConfigService).delete(1L);

            mockMvc.perform(delete("/v2/payment/account-posting/config/1"))
                    .andExpect(status().isNoContent());

            verify(postingConfigService).delete(1L);
        }

        @Test
        void returns404_whenConfigNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("PostingConfig", 99L))
                    .when(postingConfigService).delete(99L);

            mockMvc.perform(delete("/v2/payment/account-posting/config/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            doThrow(new RuntimeException("DB error")).when(postingConfigService).delete(any());

            mockMvc.perform(delete("/v2/payment/account-posting/config/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /v2/payment/account-posting/config/cache/flush ────────────────────

    @Nested
    class FlushCache {

        @Test
        void returns204_onSuccess() throws Exception {
            doNothing().when(postingConfigService).flushCache();

            mockMvc.perform(post("/v2/payment/account-posting/config/cache/flush"))
                    .andExpect(status().isNoContent());

            verify(postingConfigService).flushCache();
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            doThrow(new RuntimeException("Cache error")).when(postingConfigService).flushCache();

            mockMvc.perform(post("/v2/payment/account-posting/config/cache/flush"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
