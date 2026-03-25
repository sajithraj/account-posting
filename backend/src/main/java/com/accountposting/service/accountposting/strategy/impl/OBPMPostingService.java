package com.accountposting.service.accountposting.strategy.impl;

import com.accountposting.dto.ExternalCallResultV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.mapper.AccountPostingLegMapperV2;
import com.accountposting.mapper.AccountPostingMapperV2;
import com.accountposting.service.accountposting.strategy.ExternalApiHelper;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import com.accountposting.utils.AppUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OBPMPostingService implements PostingStrategy {

    private final AccountPostingLegServiceV2 legService;
    private final AccountPostingLegMapperV2 legMapper;
    private final AccountPostingMapperV2 postingMapper;
    private final AppUtility appUtility;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "OBPM";
    private static final String OPERATION = "POSTING";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponseV2 process(Long postingId, int legOrder, AccountPostingRequestV2 request,
                                 boolean isRetry, Long existingLegId) {
        log.info("OBPM {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        // ── Build OBPM request ─────────────────────────────────────────────
        Map<String, Object> obpmRequest = externalApiHelper.buildObpmRequest(request);
        String requestPayloadJson = appUtility.toObjectToString(obpmRequest);
        log.info("OBPM REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "OBPM | postingId=" + postingId + " leg=" + legOrder + " - existingLegId must be pre-inserted before strategy execution");
        }
        Long legId = existingLegId;

        // ── Invoke OBPM ────────────────────────────────────────────────────
        Map<String, Object> obpmResponse = externalApiHelper.callObpm(obpmRequest);
        String responsePayloadJson = appUtility.toObjectToString(obpmResponse);
        log.info("OBPM RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String obpmStatus = String.valueOf(obpmResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(obpmStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(obpmResponse.get("transaction_id"));

        // ── Update leg with result ─────────────────────────────────────────
        ExternalCallResultV2 result = new ExternalCallResultV2(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "OBPM returned status: " + obpmStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponseV2 updated = legService.updateLeg(postingId, legId,
                legMapper.toUpdateLegRequest(result));
        log.info("OBPM | leg#{} finalStatus={}", legId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }
}
