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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CBSPostingService implements PostingStrategy {

    private final AccountPostingLegService legService;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingMapper postingMapper;
    private final MappingUtils mappingUtils;
    private final ExternalApiHelper externalApiHelper;

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
        Map<String, Object> cbsRequest = externalApiHelper.buildCbsRequest(request, transactionIndex);
        String requestPayloadJson = mappingUtils.toJson(cbsRequest);
        log.info("CBS REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "CBS | postingId=" + postingId + " leg=" + legOrder + " — existingLegId must be pre-inserted before strategy execution");
        }
        Long legId = existingLegId;

        // ── Invoke CBS ─────────────────────────────────────────────────────
        Map<String, Object> cbsResponse = externalApiHelper.callCbs(cbsRequest, transactionIndex);
        String responsePayloadJson = mappingUtils.toJson(cbsResponse);
        log.info("CBS RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));

        // ── Update leg with result ─────────────────────────────────────────
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
}
