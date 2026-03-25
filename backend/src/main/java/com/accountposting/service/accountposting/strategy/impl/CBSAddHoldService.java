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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CBSAddHoldService implements PostingStrategy {

    private final AccountPostingLegServiceV2 legService;
    private final AccountPostingLegMapperV2 legMapper;
    private final AccountPostingMapperV2 postingMapper;
    private final AppUtility appUtility;
    private final ExternalApiHelper externalApiHelper;

    private static final String TARGET_SYSTEM = "CBS";
    private static final String OPERATION = "ADD_HOLD";

    @Override
    public String getPostingFlow() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public LegResponseV2 process(Long postingId, int legOrder, AccountPostingRequestV2 request,
                                 boolean isRetry, Long existingLegId) {
        log.info("CBS_ADD_HOLD {} | postingId={} flow={}", isRetry ? "RETRY" : "CREATE", postingId, getPostingFlow());

        String transactionIndex = UUID.randomUUID().toString();
        Map<String, Object> cbsRequest = externalApiHelper.buildCbsAddHoldRequest(request, transactionIndex);
        String requestPayloadJson = appUtility.toObjectToString(cbsRequest);
        log.info("CBS_ADD_HOLD REQUEST | postingId={} leg={} {}", postingId, legOrder, requestPayloadJson);

        if (existingLegId == null) {
            throw new IllegalArgumentException(
                    "CBS_ADD_HOLD | postingId=" + postingId + " leg=" + legOrder + " - existingLegId must be pre-inserted before strategy execution");
        }

        Map<String, Object> cbsResponse = externalApiHelper.callCbsAddHold(cbsRequest, transactionIndex);
        String responsePayloadJson = appUtility.toObjectToString(cbsResponse);
        log.info("CBS_ADD_HOLD RESPONSE | postingId={} leg={} {}", postingId, legOrder, responsePayloadJson);

        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String finalStatus = success ? "SUCCESS" : "FAILED";
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));

        ExternalCallResultV2 result = new ExternalCallResultV2(
                LegStatus.valueOf(finalStatus),
                referenceId,
                success ? null : "CBS_ADD_HOLD returned status: " + cbsStatus,
                requestPayloadJson,
                responsePayloadJson,
                isRetry ? LegMode.RETRY : LegMode.NORM
        );
        AccountPostingLegResponseV2 updated = legService.updateLeg(postingId, existingLegId,
                legMapper.toUpdateLegRequest(result));
        log.info("CBS_ADD_HOLD | leg#{} finalStatus={}", existingLegId, updated.getStatus());

        return postingMapper.toLegResponse(updated);
    }
}
