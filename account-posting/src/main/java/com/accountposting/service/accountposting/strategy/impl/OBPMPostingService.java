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
public class OBPMPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final MappingUtils mappingUtils;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "OBPM";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponse process(Long postingId, int legOrder, AccountPostingRequest request,
                               boolean isRetry, Long existingLegId) {
        log.info("OBPM {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build OBPM request ─────────────────────────────────────────────
        Map<String, Object> obpmRequest = externalApiHelper.buildObpmRequest(request);
        String requestPayloadJson = mappingUtils.toJson(obpmRequest);
        log.info("OBPM REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "OBPM | postingId=" + postingId + " leg=" + legOrder + " — existingLegId must be pre-inserted before strategy execution");
        }
        Long legId = existingLegId;

        // ── Invoke OBPM ────────────────────────────────────────────────────
        Map<String, Object> obpmResponse = externalApiHelper.callObpm(obpmRequest);
        String responsePayloadJson = mappingUtils.toJson(obpmResponse);
        log.info("OBPM RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String obpmStatus = String.valueOf(obpmResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(obpmStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(obpmResponse.get("transaction_id"));

        // ── Update leg with result ─────────────────────────────────────────
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
}
