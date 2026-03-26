package com.sajith.payments.redesign.service.retry;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategyFactory;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Processes the retry of a single posting within its own transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingRetryProcessorV2 {

    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegServiceV2 legService;
    private final PostingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;

    @Transactional
    public boolean process(Long postingId) {
        MDC.put("postingId", String.valueOf(postingId));
        log.info("Retry processing started for posting id :: {} . Thread :: {} .", postingId, Thread.currentThread().getName());
        AccountPostingEntity posting = postingRepository.findById(postingId).orElse(null);
        if (posting == null) {
            log.error("Posting not found, skipping retry for posting id :: {} .", postingId);
            return false;
        }

        MDC.put("e2eRef", posting.getEndToEndReferenceId());
        MDC.put("requestType", posting.getRequestType());

        AccountPostingRequestV2 originalRequest = deserializeRequest(posting.getRequestPayload());
        if (originalRequest == null) {
            log.error("Original request payload is missing or unreadable, skipping for posting id :: {} .", postingId);
            return false;
        }

        List<AccountPostingLegResponseV2> pendingLegs = legService.listNonSuccessLegs(postingId);
        log.info("Non-SUCCESS legs to retry for postingId :: {} count :: {} legs :: {} .",
                postingId, pendingLegs.size(),
                pendingLegs.stream().map(l -> l.getTargetSystem() + "#" + l.getLegOrder() + "=" + l.getStatus()).toList());

        if (pendingLegs.isEmpty()) {
            log.info("No legs available to retry for posting id :: {} .", postingId);
            return posting.getStatus() == PostingStatus.ACSP;
        }

        String lastFailReason = null;
        for (AccountPostingLegResponseV2 leg : pendingLegs) {
            log.info("Retrying leg for posting id :: {} posting leg id :: {} legOrder :: {} target system :: {} .", postingId, leg.getPostingLegId(), leg.getLegOrder(), leg.getTargetSystem());
            try {
                PostingStrategy strategy = strategyFactory.resolve(leg.getTargetSystem() + "_" + leg.getOperation());
                LegResponseV2 legResult = strategy.process(postingId, leg.getLegOrder(), originalRequest, true, leg.getPostingLegId());
                log.info("Leg retry completed for posting leg id :: {} status from {} to {} .", leg.getPostingLegId(), leg.getStatus(), legResult.getStatus());
                if (!"SUCCESS".equals(legResult.getStatus()) && legResult.getReason() != null && !legResult.getReason().isBlank()) {
                    lastFailReason = legResult.getReason();
                }
            } catch (Exception ex) {
                log.error("Leg retry failed with exception for posting id :: {} posting leg id :: {} target system :: {}. Error message :: {} .",
                        postingId, leg.getPostingLegId(), leg.getTargetSystem(), ex.getMessage(), ex);
                lastFailReason = ex.getMessage();
            }
        }

        // Promote posting to ACSP only when every leg is now SUCCESS.
        List<AccountPostingLegResponseV2> allLegs = legService.listLegs(postingId);
        boolean fullySucceeded = allLegs.stream().allMatch(l -> l.getStatus() == LegStatus.SUCCESS);
        PostingStatus newStatus = fullySucceeded ? PostingStatus.ACSP : PostingStatus.PNDG;
        String reason = fullySucceeded
                ? "Request processed successfully"
                : (lastFailReason != null ? lastFailReason : "One or more legs failed after retry");

        posting.setStatus(newStatus);
        posting.setReason(reason);
        posting.setRetryLockedUntil(null);
        postingRepository.save(posting);
        log.info("Retry finished for posting id :: {} posting status :: {} .", postingId, newStatus);

        return fullySucceeded;
    }

    private AccountPostingRequestV2 deserializeRequest(String requestPayload) {
        if (requestPayload == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(requestPayload);
            String json = node.isTextual() ? node.textValue() : requestPayload;
            return objectMapper.readValue(json, AccountPostingRequestV2.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize requestPayload. Error message :: {} .", ex.getMessage(), ex);
            return null;
        }
    }
}
