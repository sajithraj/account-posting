package com.sajith.payments.redesign.service.retry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategyFactory;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

        IncomingPostingRequest originalRequest = deserializeRequest(posting.getRequestPayload());
        if (originalRequest == null) {
            log.error("Original request payload is missing or unreadable, skipping for posting id :: {} .", postingId);
            return false;
        }

        List<AccountPostingLegResponseV2> pendingLegs = legService.listNonSuccessLegs(postingId);
        log.info("Non-SUCCESS legs to retry for postingId :: {} count :: {} legs :: {} .",
                postingId, pendingLegs.size(),
                pendingLegs.stream().map(l -> l.getTargetSystem() + "#" + l.getTransactionOrder() + "=" + l.getStatus()).toList());

        if (pendingLegs.isEmpty()) {
            log.info("No legs available to retry for posting id :: {} .", postingId);
            return posting.getStatus() == PostingStatus.ACSP;
        }

        String lastFailReason = null;
        for (AccountPostingLegResponseV2 leg : pendingLegs) {
            log.info("Retrying transaction for posting id :: {} transaction id :: {} order :: {} target system :: {} .", postingId, leg.getTransactionId(), leg.getTransactionOrder(), leg.getTargetSystem());
            try {
                PostingStrategy strategy = strategyFactory.resolve(leg.getTargetSystem() + "_" + leg.getOperation());
                LegResponseV2 legResult = strategy.process(postingId, leg.getTransactionOrder(), originalRequest, true, leg.getTransactionId());
                log.info("Transaction retry completed for transaction id :: {} status from {} to {} .", leg.getTransactionId(), leg.getStatus(), legResult.getStatus());
                if (!"SUCCESS".equals(legResult.getStatus()) && legResult.getReason() != null && !legResult.getReason().isBlank()) {
                    lastFailReason = legResult.getReason();
                }
            } catch (Exception ex) {
                log.error("Transaction retry failed with exception for posting id :: {} transaction id :: {} target system :: {}. Error message :: {} .",
                        postingId, leg.getTransactionId(), leg.getTargetSystem(), ex.getMessage(), ex);
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

    private IncomingPostingRequest deserializeRequest(String requestPayload) {
        if (requestPayload == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(requestPayload);
            String json = node.isTextual() ? node.textValue() : requestPayload;
            return objectMapper.readValue(json, IncomingPostingRequest.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize requestPayload. Error message :: {} .", ex.getMessage(), ex);
            return null;
        }
    }
}
