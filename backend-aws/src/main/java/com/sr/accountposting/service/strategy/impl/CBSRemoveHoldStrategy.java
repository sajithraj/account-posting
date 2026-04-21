package com.sr.accountposting.service.strategy.impl;

import com.sr.accountposting.dto.ExternalCallResult;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.enums.LegStatus;
import com.sr.accountposting.service.strategy.ExternalApiHelper;
import com.sr.accountposting.service.strategy.PostingStrategy;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
public class CBSRemoveHoldStrategy implements PostingStrategy {

    private static final Logger log = LoggerFactory.getLogger(CBSRemoveHoldStrategy.class);

    private static final String TARGET_SYSTEM = "CBS";
    private static final String OPERATION = "REMOVE_HOLD";

    private final ExternalApiHelper externalApiHelper;

    @Inject
    public CBSRemoveHoldStrategy(ExternalApiHelper externalApiHelper) {
        this.externalApiHelper = externalApiHelper;
    }

    @Override
    public String getFlowKey() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config) {
        String transactionIndex = UUID.randomUUID().toString();
        Map<String, Object> cbsRequest = externalApiHelper.buildCbsRemoveHoldRequest(request, transactionIndex);
        String requestPayloadJson = JsonUtil.toJson(cbsRequest);
        log.info("Calling CBS REMOVE_HOLD for e2e={}", request.getEndToEndReferenceId());
        log.debug("CBS REMOVE_HOLD request payload: {}", requestPayloadJson);

        Map<String, Object> cbsResponse = externalApiHelper.callCbsRemoveHold(cbsRequest, transactionIndex);
        String responsePayloadJson = JsonUtil.toJson(cbsResponse);

        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));

        log.info("CBS REMOVE_HOLD responded for e2e={} Ã¢â‚¬â€ status={} ref={}", request.getEndToEndReferenceId(), cbsStatus, referenceId);
        log.debug("CBS REMOVE_HOLD response payload: {}", responsePayloadJson);
        String postedTime = cbsResponse.get("posted_time") != null
                ? String.valueOf(cbsResponse.get("posted_time")) : null;

        return ExternalCallResult.builder()
                .status(success ? LegStatus.SUCCESS : LegStatus.FAILED)
                .referenceId(referenceId)
                .postedTime(postedTime)
                .reason(success ? null : "CBS_REMOVE_HOLD returned status: " + cbsStatus)
                .requestPayload(requestPayloadJson)
                .responsePayload(responsePayloadJson)
                .build();
    }
}
