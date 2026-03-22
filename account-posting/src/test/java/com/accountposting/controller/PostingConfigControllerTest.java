package com.accountposting.controller;

import com.accountposting.dto.config.PostingConfigRequest;
import com.accountposting.dto.config.PostingConfigResponse;
import com.accountposting.exception.GlobalExceptionHandler;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.service.config.PostingConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostingConfigController.class)
@Import(GlobalExceptionHandler.class)
class PostingConfigControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PostingConfigService service;

    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private PostingConfigRequest validRequest() {
        PostingConfigRequest req = new PostingConfigRequest();
        req.setSourceName("DMS");
        req.setRequestType("CBS_GL");
        req.setTargetSystem("CBS");
        req.setOperation("POSTING");
        req.setOrderSeq(1);
        return req;
    }

    private PostingConfigResponse sampleResponse(Long configId) {
        PostingConfigResponse resp = new PostingConfigResponse();
        resp.setConfigId(configId);
        resp.setSourceName("DMS");
        resp.setRequestType("CBS_GL");
        resp.setTargetSystem("CBS");
        resp.setOperation("POSTING");
        resp.setOrderSeq(1);
        return resp;
    }

    // ── GET /account-posting/config ────────────────────────────────────────────

    @Nested
    class GetAll {

        @Test
        void returns200_withAllConfigs() throws Exception {
            when(service.getAll()).thenReturn(
                    List.of(sampleResponse(1L), sampleResponse(2L)));

            mockMvc.perform(get("/account-posting/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].configId").value(1))
                    .andExpect(jsonPath("$[0].requestType").value("CBS_GL"));
        }

        @Test
        void returns200_withEmptyList() throws Exception {
            when(service.getAll()).thenReturn(List.of());

            mockMvc.perform(get("/account-posting/config"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.getAll()).thenThrow(new RuntimeException("DB error"));

            mockMvc.perform(get("/account-posting/config"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── GET /account-posting/config/{requestType} ─────────────────────────────

    @Nested
    class GetByRequestType {

        @Test
        void returns200_withMatchingConfigs() throws Exception {
            when(service.getByRequestType("CBS_GL"))
                    .thenReturn(List.of(sampleResponse(1L), sampleResponse(2L)));

            mockMvc.perform(get("/account-posting/config/CBS_GL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void returns200_withEmptyListForUnknownType() throws Exception {
            when(service.getByRequestType("UNKNOWN")).thenReturn(List.of());

            mockMvc.perform(get("/account-posting/config/UNKNOWN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.getByRequestType(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(get("/account-posting/config/CBS_GL"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /account-posting/config ───────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void returns201_whenValidRequest() throws Exception {
            when(service.create(any())).thenReturn(sampleResponse(10L));

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.configId").value(10))
                    .andExpect(jsonPath("$.targetSystem").value("CBS"))
                    .andExpect(jsonPath("$.orderSeq").value(1));
        }

        @Test
        void returns400_whenAllRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/account-posting/config")
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
            PostingConfigRequest req = validRequest();
            req.setSourceName("  ");

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='sourceName')]").exists());
        }

        @Test
        void returns400_whenOrderSeqIsZero() throws Exception {
            PostingConfigRequest req = validRequest();
            req.setOrderSeq(0);

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='orderSeq')]").exists());
        }

        @Test
        void returns400_whenOrderSeqIsNegative() throws Exception {
            PostingConfigRequest req = validRequest();
            req.setOrderSeq(-1);

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[?(@.field=='orderSeq')]").exists());
        }

        @Test
        void returns422_whenDuplicateConfigOrder() throws Exception {
            when(service.create(any())).thenThrow(
                    new DataIntegrityViolationException(
                            "could not execute statement [uq_posting_config_request_type_order]"));

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.name").value("DUPLICATE_CONFIG_ORDER"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.create(any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(post("/account-posting/config")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── PUT /account-posting/config/{configId} ─────────────────────────────────

    @Nested
    class Update {

        @Test
        void returns200_whenValidRequest() throws Exception {
            PostingConfigResponse updated = sampleResponse(5L);
            updated.setOrderSeq(2);
            when(service.update(eq(5L), any())).thenReturn(updated);

            PostingConfigRequest req = validRequest();
            req.setOrderSeq(2);

            mockMvc.perform(put("/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.configId").value(5))
                    .andExpect(jsonPath("$.orderSeq").value(2));
        }

        @Test
        void returns400_whenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(put("/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.name").value("VALIDATION_FAILED"));
        }

        @Test
        void returns404_whenConfigNotFound() throws Exception {
            when(service.update(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("PostingConfig", 999L));

            mockMvc.perform(put("/account-posting/config/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value(containsString("999")));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            when(service.update(any(), any())).thenThrow(new RuntimeException("Unexpected"));

            mockMvc.perform(put("/account-posting/config/5")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── DELETE /account-posting/config/{configId} ──────────────────────────────

    @Nested
    class Delete {

        @Test
        void returns204_whenDeleted() throws Exception {
            doNothing().when(service).delete(1L);

            mockMvc.perform(delete("/account-posting/config/1"))
                    .andExpect(status().isNoContent());

            verify(service).delete(1L);
        }

        @Test
        void returns404_whenConfigNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("PostingConfig", 99L))
                    .when(service).delete(99L);

            mockMvc.perform(delete("/account-posting/config/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.name").value("NOT_FOUND"));
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            doThrow(new RuntimeException("DB error")).when(service).delete(any());

            mockMvc.perform(delete("/account-posting/config/1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }

    // ── POST /account-posting/config/cache/flush ───────────────────────────────

    @Nested
    class FlushCache {

        @Test
        void returns204_onSuccess() throws Exception {
            doNothing().when(service).flushCache();

            mockMvc.perform(post("/account-posting/config/cache/flush"))
                    .andExpect(status().isNoContent());

            verify(service).flushCache();
        }

        @Test
        void returns500_whenServiceThrows() throws Exception {
            doThrow(new RuntimeException("Cache error")).when(service).flushCache();

            mockMvc.perform(post("/account-posting/config/cache/flush"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.name").value("INTERNAL_ERROR"));
        }
    }
}
