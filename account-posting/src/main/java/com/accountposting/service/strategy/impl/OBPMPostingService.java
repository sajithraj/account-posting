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

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OBPM posting strategy.
 * <p>
 * CREATE flow:
 * 1. Insert leg as PENDING (DB write)
 * 2. Invoke OBPM (stub)
 * 3. Update leg to SUCCESS / FAILED based on response (DB write)
 * <p>
 * RETRY flow:
 * 1. Invoke OBPM (stub)
 * 2. Update existing leg (mode=RETRY → increments attemptNumber) (DB write)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OBPMPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final ObjectMapper objectMapper;

    private static final String TARGET_SYSTEM = "OBPM";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponse process(Long postingId, int legOrder, AccountPostingRequest request,
                               boolean isRetry, Long existingLegId) {
        log.info("OBPM {} | flow={}", isRetry ? "RETRY" : "CREATE", getPostingFlow());

        // ── Build OBPM request ─────────────────────────────────────────────
        Map<String, Object> obpmRequest = buildObpmRequest(request);
        String requestPayloadJson = toJson(obpmRequest);

        Long legId;
        if (existingLegId == null) {
            AccountPostingLegRequest legRequest = legMapper.toCreateLegRequest(
                    request, legOrder, TARGET_SYSTEM, LegMode.NORM, OPERATION, requestPayloadJson);
            AccountPostingLegResponse pending = legService.addLeg(postingId, legRequest);
            legId = pending.getPostingLegId();
            log.info("OBPM | self-inserted PENDING leg#{}", legId);
        } else {
            legId = existingLegId;
        }

        // ── Step 2: invoke OBPM (stub — replace with real HTTP call) ──────
        Map<String, Object> obpmResponse = callObpm(obpmRequest);
        String obpmStatus = String.valueOf(obpmResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(obpmStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(obpmResponse.get("transaction_id"));
        String responsePayloadJson = toJson(obpmResponse);
        log.info("OBPM | leg#{} obpmStatus={}", legId, obpmStatus);

        // ── Step 3: update leg with result ────────────────────────────────
        ExternalCallResult result = new ExternalCallResult(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "OBPM returned status: " + obpmStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponse updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("OBPM | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }

    // ── OBPM request / response structure ─────────────────────────────────

    private Map<String, Object> buildObpmRequest(AccountPostingRequest request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "OBPM");
        req.put("amount", request.getAmount());
        req.put("currency", request.getCurrency());
        req.put("remit_info1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("mca_code", 97);
        req.put("mca_account", request.getDebtorAccount());
        return req;
    }

    /**
     * Stub — replace with real OBPM HTTP call.
     * OBPM response: { transaction_id, status: SUCCESS|FAILURE }
     */
    private Map<String, Object> callObpm(Map<String, Object> obpmRequest) {
        // TODO: replace with actual OBPM HTTP integration
        String transactionId = "TRAN" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(java.time.LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
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
