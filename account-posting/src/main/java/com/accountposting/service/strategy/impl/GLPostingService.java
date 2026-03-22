package com.accountposting.service.strategy.impl;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.ExternalCallResult;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.mapper.AccountPostingMapper;
import com.accountposting.service.AccountPostingLegService;
import com.accountposting.service.strategy.PostingStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GL posting strategy.
 * <p>
 * CREATE flow:
 * 1. Insert leg as PENDING (DB write)
 * 2. Invoke GL (stub)
 * 3. Update leg to SUCCESS / FAILED based on response (DB write)
 * <p>
 * RETRY flow:
 * 1. Invoke GL (stub)
 * 2. Update existing leg (mode=RETRY → increments attemptNumber) (DB write)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GLPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final ObjectMapper objectMapper;

    private static final String TARGET_SYSTEM = "GL";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponse process(Long postingId, int legOrder, AccountPostingRequest request,
                               boolean isRetry, Long existingLegId) {
        log.info("GL {} | flow={}", isRetry ? "RETRY" : "CREATE", getPostingFlow());

        // ── Build GL request ───────────────────────────────────────────────
        Map<String, Object> glRequest = buildGlRequest(request);
        String requestPayloadJson = toJson(glRequest);

        Long legId;
        if (existingLegId == null) {
            AccountPostingLegRequest legRequest = legMapper.toCreateLegRequest(
                    request, legOrder, TARGET_SYSTEM, LegMode.NORM, OPERATION, requestPayloadJson);
            AccountPostingLegResponse pending = legService.addLeg(postingId, legRequest);
            legId = pending.getPostingLegId();
            log.info("GL | self-inserted PENDING leg#{}", legId);
        } else {
            legId = existingLegId;
        }

        // ── Step 2: invoke GL (stub — replace with real HTTP call) ────────
        Map<String, Object> glResponse = callGl(glRequest);
        String glStatus = String.valueOf(glResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(glStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(glResponse.get("responder_ref_id"));
        String responsePayloadJson = toJson(glResponse);
        log.info("GL | leg#{} glStatus={}", legId, glStatus);

        // ── Step 3: update leg with result ────────────────────────────────
        ExternalCallResult result = new ExternalCallResult(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "GL returned status: " + glStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponse updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("GL | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }

    // ── GL request / response structure ───────────────────────────────────

    private Map<String, Object> buildGlRequest(AccountPostingRequest request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "GL");
        req.put("amount", request.getAmount());
        req.put("rem_info", List.of(request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment"));
        req.put("dept_code", 12);
        req.put("gl_account", request.getDebtorAccount());
        return req;
    }

    /**
     * Stub — replace with real GL HTTP call.
     * GL response: { responder_ref_id, status: SUCCESS|FAILURE }
     */
    private Map<String, Object> callGl(Map<String, Object> glRequest) {
        // TODO: replace with actual GL HTTP integration
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responder_ref_id", UUID.randomUUID().toString());
        response.put("status", "SUCCESS");
        return response;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
