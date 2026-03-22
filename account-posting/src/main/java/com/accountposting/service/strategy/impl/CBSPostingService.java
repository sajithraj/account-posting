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
import java.util.Map;
import java.util.UUID;

/**
 * CBS posting strategy.
 * <p>
 * CREATE flow:
 * 1. Insert leg as PENDING (DB write)
 * 2. Invoke CBS (stub)
 * 3. Update leg to SUCCESS / FAILED based on response (DB write)
 * <p>
 * RETRY flow:
 * 1. Invoke CBS (stub)
 * 2. Update existing leg (mode=RETRY → increments attemptNumber) (DB write)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CBSPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final ObjectMapper objectMapper;

    private static final String TARGET_SYSTEM = "CBS";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponse process(Long postingId, int legOrder, AccountPostingRequest request,
                               boolean isRetry, Long existingLegId) {
        log.info("CBS {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build CBS request ──────────────────────────────────────────────
        String transactionIndex = UUID.randomUUID().toString();
        Map<String, Object> cbsRequest = buildCbsRequest(request, transactionIndex);
        String requestPayloadJson = toJson(cbsRequest);

        // Leg is always pre-inserted by the caller; existingLegId is always non-null on CREATE.
        // Only falls back to self-insert on direct/legacy calls where no pre-insert happened.
        Long legId;
        if (existingLegId == null) {
            AccountPostingLegRequest legRequest = legMapper.toCreateLegRequest(
                    request, legOrder, TARGET_SYSTEM, LegMode.NORM, OPERATION, requestPayloadJson);
            AccountPostingLegResponse pending = legService.addLeg(postingId, legRequest);
            legId = pending.getPostingLegId();
            log.info("CBS | self-inserted PENDING leg#{}", legId);
        } else {
            legId = existingLegId;
        }

        // ── Step 2: invoke CBS (stub — replace with real HTTP call) ───────
        Map<String, Object> cbsResponse = callCbs(cbsRequest, transactionIndex);
        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));
        String responsePayloadJson = toJson(cbsResponse);
        log.info("CBS | leg#{} cbsStatus={}", legId, cbsStatus);

        // ── Step 3: update leg with result ────────────────────────────────
        ExternalCallResult result = new ExternalCallResult(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "CBS returned status: " + cbsStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponse updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("CBS | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }

    // ── CBS request / response structure ──────────────────────────────────

    private Map<String, Object> buildCbsRequest(AccountPostingRequest request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("amount", request.getAmount());
        req.put("description1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("transaction_code", 9034);
        req.put("tell_id", 4516);
        req.put("account", request.getDebtorAccount());
        return req;
    }

    /**
     * Stub — replace with real CBS HTTP call.
     * CBS response: { transaction_index, status: SUCCESS|FAILURE }
     */
    private Map<String, Object> callCbs(Map<String, Object> cbsRequest, String transactionIndex) {
        // TODO: replace with actual CBS HTTP integration
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
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
