package com.sajith.payments.redesign.service.accountposting.strategy.impl;

import com.sajith.payments.redesign.dto.ExternalCallResultV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GLPostingService implements PostingStrategy {

    private final AccountPostingLegServiceV2 legService;
    private final AccountPostingLegMapperV2 legMapper;
    private final AccountPostingMapperV2 postingMapper;
    private final AppUtility appUtility;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "GL";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponseV2 process(Long postingId, int transactionOrder, IncomingPostingRequest request,
                                 boolean isRetry, Long existingTxnId) {
        log.info("GL {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build GL request ───────────────────────────────────────────────
        Map<String, Object> glRequest = externalApiHelper.buildGlRequest(request);
        String requestPayloadJson = appUtility.toObjectToString(glRequest);
        log.info("GL REQUEST | postingId={} txnOrder={} {}", postingId, transactionOrder, requestPayloadJson);

        if (existingTxnId == null) {
            throw new IllegalArgumentException(
                    "GL | postingId=" + postingId + " txnOrder=" + transactionOrder + " - existingTxnId must be pre-inserted before strategy execution");
        }
        Long legId = existingTxnId;

        // ── Invoke GL ──────────────────────────────────────────────────────
        Map<String, Object> glResponse = externalApiHelper.callGl(glRequest);
        String responsePayloadJson = appUtility.toObjectToString(glResponse);
        log.info("GL RESPONSE | postingId={} txnOrder={} {}", postingId, transactionOrder, responsePayloadJson);

        String glStatus = String.valueOf(glResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(glStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(glResponse.get("responder_ref_id"));
        String postedTime = glResponse.get("posted_time") != null
                ? String.valueOf(glResponse.get("posted_time")) : null;

        // ── Update leg with result ─────────────────────────────────────────
        ExternalCallResultV2 result = new ExternalCallResultV2(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "GL returned status: " + glStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM,
                postedTime
        );
        AccountPostingLegResponseV2 updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("GL | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }
}
