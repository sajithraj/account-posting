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
public class GLPostingStrategy implements PostingStrategy {

    private static final Logger log = LoggerFactory.getLogger(GLPostingStrategy.class);

    private static final String TARGET_SYSTEM = "GL";
    private static final String OPERATION = "POSTING";

    private final ExternalApiHelper externalApiHelper;

    @Inject
    public GLPostingStrategy(ExternalApiHelper externalApiHelper) {
        this.externalApiHelper = externalApiHelper;
    }

    @Override
    public String getFlowKey() {
        return TARGET_SYSTEM + "_" + OPERATION;
    }

    @Override
    public ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config) {
        Map<String, Object> glRequest = externalApiHelper.buildGlRequest(request);
        String requestPayloadJson = JsonUtil.toJson(glRequest);
        log.info("Calling GL for e2e={}", request.getEndToEndReferenceId());
        log.debug("GL request payload: {}", requestPayloadJson);

        Map<String, Object> glResponse = externalApiHelper.callGl(glRequest);
        String responsePayloadJson = JsonUtil.toJson(glResponse);

        String glStatus = String.valueOf(glResponse.get("status"));
        boolean success = "SUCCESS".equalsIgnoreCase(glStatus);
        String referenceId = String.valueOf(glResponse.get("responder_ref_id"));
        String postedTime = glResponse.get("posted_time") != null
                ? String.valueOf(glResponse.get("posted_time")) : null;

        log.info("GL responded for e2e={} Ã¢â‚¬â€ status={} ref={}", request.getEndToEndReferenceId(), glStatus, referenceId);
        log.debug("GL response payload: {}", responsePayloadJson);

        return ExternalCallResult.builder()
                .status(success ? LegStatus.SUCCESS : LegStatus.FAILED)
                .referenceId(referenceId)
                .postedTime(postedTime)
                .reason(success ? null : "GL returned status: " + glStatus)
                .requestPayload(requestPayloadJson)
                .responsePayload(responsePayloadJson)
                .build();
    }
}
