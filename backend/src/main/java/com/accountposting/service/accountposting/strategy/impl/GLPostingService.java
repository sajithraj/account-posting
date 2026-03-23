package com.accountposting.service.accountposting.strategy.impl;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.ExternalCallResult;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.mapper.AccountPostingMapper;
import com.accountposting.mapper.MappingUtils;
import com.accountposting.service.accountposting.strategy.ExternalApiHelper;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountpostingleg.AccountPostingLegService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GLPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final MappingUtils mappingUtils;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "GL";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponse process(Long postingId, int legOrder, AccountPostingRequest request,
                               boolean isRetry, Long existingLegId) {
        log.info("GL {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build GL request ───────────────────────────────────────────────
        Map<String, Object> glRequest = externalApiHelper.buildGlRequest(request);
        String requestPayloadJson = mappingUtils.toJson(glRequest);
        log.info("GL REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "GL | postingId=" + postingId + " leg=" + legOrder + " — existingLegId must be pre-inserted before strategy execution");
        }
        Long legId = existingLegId;

        // ── Invoke GL ──────────────────────────────────────────────────────
        Map<String, Object> glResponse = externalApiHelper.callGl(glRequest);
        String responsePayloadJson = mappingUtils.toJson(glResponse);
        log.info("GL RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String glStatus = String.valueOf(glResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(glStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(glResponse.get("responder_ref_id"));

        // ── Update leg with result ─────────────────────────────────────────
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
}
