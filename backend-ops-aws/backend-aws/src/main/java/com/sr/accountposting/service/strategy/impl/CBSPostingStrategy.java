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
public class CBSPostingStrategy implements PostingStrategy {

    private static final Logger log = LoggerFactory.getLogger(CBSPostingStrategy.class);

    private static final String TARGET_SYSTEM = "CBS";
    private static final String OPERATION = "POSTING";

    private final ExternalApiHelper externalApiHelper;

    @Inject
    public CBSPostingStrategy(ExternalApiHelper externalApiHelper) {
        this.externalApiHelper = externalApiHelper;
    }

    @Override
    public String getFlowKey() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config) {
        String transactionIndex = UUID.randomUUID().toString();
        Map<String, Object> cbsRequest = externalApiHelper.buildCbsRequest(request, transactionIndex);
        String requestPayloadJson = JsonUtil.toJson(cbsRequest);
        log.info("Calling CBS for e2e={}", request.getEndToEndReferenceId());
        log.debug("CBS request payload: {}", requestPayloadJson);

        Map<String, Object> cbsResponse = externalApiHelper.callCbs(cbsRequest, transactionIndex);
        String responsePayloadJson = JsonUtil.toJson(cbsResponse);

        String cbsStatus = String.valueOf(cbsResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(cbsStatus);
        String referenceId = String.valueOf(cbsResponse.get("transaction_index"));
        String postedTime = cbsResponse.get("posted_time") != null
                ? String.valueOf(cbsResponse.get("posted_time")) : null;

        log.info("CBS responded for e2e={} — status={} ref={}", request.getEndToEndReferenceId(), cbsStatus, referenceId);
        log.debug("CBS response payload: {}", responsePayloadJson);

        return ExternalCallResult.builder()
                .status(success ? LegStatus.SUCCESS : LegStatus.FAILED)
                .referenceId(referenceId)
                .postedTime(postedTime)
                .reason(success ? null : "CBS returned status: " + cbsStatus)
                .requestPayload(requestPayloadJson)
                .responsePayload(responsePayloadJson)
                .build();
    }
}
