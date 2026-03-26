package com.sajith.payments.redesign.service.accountposting.strategy.impl;

import com.sajith.payments.redesign.dto.ExternalCallResultV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.mapper.AccountPostingLegMapperV2;
import com.sajith.payments.redesign.mapper.AccountPostingMapperV2;
import com.sajith.payments.redesign.service.accountposting.strategy.ExternalApiHelper;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
import com.sajith.payments.redesign.utils.AppUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CBSPostingService implements PostingStrategy {

    private final AccountPostingLegServiceV2 legService;
    private final AccountPostingLegMapperV2 legMapper;
    private final AccountPostingMapperV2 postingMapper;
    private final AppUtility appUtility;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "CBS";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponseV2 process(Long postingId, int legOrder, AccountPostingRequestV2 request,
                                 boolean isRetry, Long existingLegId) {
        log.info("CBS {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build CBS request ──────────────────────────────────────────────
        String transactionIndex = UUID.randomUUID().toString();
        Map<String, Object> cbsRequest = externalApiHelper.buildCbsRequest(request, transactionIndex);
        String requestPayloadJson = appUtility.toObjectToString(cbsRequest);
        log.info("CBS REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "CBS | postingId=" + postingId + " leg=" + legOrder + " - existingLegId must be pre-inserted before strategy execution");
        }
        Long legId = existingLegId;

        // ── Invoke CBS ─────────────────────────────────────────────────────
        Map<String, Object> cbsResponse = externalApiHelper.callCbs(cbsRequest, transactionIndex);
        String responsePayloadJson = appUtility.toObjectToString(cbsResponse);
        log.info("CBS RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));

        // ── Update leg with result ─────────────────────────────────────────
        ExternalCallResultV2 result = new ExternalCallResultV2(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "CBS returned status: " + cbsStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponseV2 updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("CBS | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }
}
