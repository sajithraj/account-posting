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

@Singleton
public class OBPMPostingStrategy implements PostingStrategy {

    private static final Logger log = LoggerFactory.getLogger(OBPMPostingStrategy.class);

    private static final String TARGET_SYSTEM = "OBPM";
    private static final String OPERATION = "POSTING";

    private final ExternalApiHelper externalApiHelper;

    @Inject
    public OBPMPostingStrategy(ExternalApiHelper externalApiHelper) {
        this.externalApiHelper = externalApiHelper;
    }

    @Override
    public String getFlowKey() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config) {
        Map<String, Object> obpmRequest = externalApiHelper.buildObpmRequest(request);
        String requestPayloadJson = JsonUtil.toJson(obpmRequest);
        log.info("Calling OBPM for e2e={}", request.getEndToEndReferenceId());
        log.debug("OBPM request payload: {}", requestPayloadJson);

        Map<String, Object> obpmResponse = externalApiHelper.callObpm(obpmRequest);
        String responsePayloadJson = JsonUtil.toJson(obpmResponse);

        String obpmStatus = String.valueOf(obpmResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(obpmStatus);
        String referenceId = String.valueOf(obpmResponse.get("transaction_id"));
        String postedTime = obpmResponse.get("posted_time") != null
                ? String.valueOf(obpmResponse.get("posted_time")) : null;

        log.info("OBPM responded for e2e={} Ã¢â‚¬â€ status={} ref={}", request.getEndToEndReferenceId(), obpmStatus, referenceId);
        log.debug("OBPM response payload: {}", responsePayloadJson);

        return ExternalCallResult.builder()
                .status(success ? LegStatus.SUCCESS : LegStatus.FAILED)
                .referenceId(referenceId)
                .postedTime(postedTime)
                .reason(success ? null : "OBPM returned status: " + obpmStatus)
                .requestPayload(requestPayloadJson)
                .responsePayload(responsePayloadJson)
                .build();
    }
}
