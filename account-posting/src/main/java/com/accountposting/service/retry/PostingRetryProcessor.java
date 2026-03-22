package com.accountposting.service.retry;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Processes the retry of a single posting within its own transaction.
 * Invoked from a CompletableFuture so each posting is retried independently and in parallel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingRetryProcessor {

    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegService legService;
    private final PostingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostingEventPublisher eventPublisher;

    @Transactional
    public List<RetryResponse.LegRetryResult> process(Long postingId) {
        MDC.put("postingId", String.valueOf(postingId));
        log.info("Retry started | thread={}", Thread.currentThread().getName());

        AccountPostingEntity posting = postingRepository.findById(postingId).orElse(null);
        if (posting == null) {
            log.warn("Posting not found, skipping retry | postingId={}", postingId);
            return List.of();
        }

        MDC.put("e2eRef", posting.getEndToEndReferenceId());
        MDC.put("requestType", posting.getRequestType());

        AccountPostingRequest originalRequest = deserializeRequest(posting.getRequestPayload());
        if (originalRequest == null) {
            log.warn("Original request payload is missing or unreadable, skipping | postingId={}", postingId);
            return List.of();
        }

        List<AccountPostingLegResponse> pendingLegs = legService.listNonSuccessLegs(postingId);
        log.info("Non-SUCCESS legs to retry | postingId={} count={} legs={}",
                postingId, pendingLegs.size(),
                pendingLegs.stream().map(l -> l.getTargetSystem() + "#" + l.getLegOrder() + "=" + l.getStatus()).toList());

        if (pendingLegs.isEmpty()) {
            log.info("No legs require retry | postingId={}", postingId);
            return List.of();
        }

        List<RetryResponse.LegRetryResult> results = new ArrayList<>();

        for (AccountPostingLegResponse leg : pendingLegs) {
            log.info("Retrying leg | postingId={} postingLegId={} legOrder={} targetSystem={}",
                    postingId, leg.getPostingLegId(), leg.getLegOrder(), leg.getTargetSystem());
            try {
                PostingStrategy strategy = strategyFactory.resolve(leg.getTargetSystem() + "_" + leg.getOperation());
                LegResponse legResult = strategy.process(postingId, leg.getLegOrder(), originalRequest, true, leg.getPostingLegId());

                log.info("Leg retry completed | postingLegId={} status: {} -> {}",
                        leg.getPostingLegId(), leg.getStatus(), legResult.getStatus());

                results.add(RetryResponse.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(legResult.getStatus())
                        .reason(legResult.getReason())
                        .build());
            } catch (Exception ex) {
                log.error("Leg retry failed with exception | postingId={} postingLegId={} targetSystem={}",
                        postingId, leg.getPostingLegId(), leg.getTargetSystem(), ex);
                results.add(RetryResponse.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(LegStatus.FAILED.name())
                        .reason(ex.getMessage())
                        .build());
            }
        }

        // Promote posting to SUCCESS only when every leg across all attempts is now SUCCESS.
        List<AccountPostingLegResponse> allLegs = legService.listLegs(postingId);
        boolean fullySucceeded = allLegs.stream().allMatch(l -> l.getStatus() == LegStatus.SUCCESS);
        PostingStatus newStatus = fullySucceeded ? PostingStatus.SUCCESS : PostingStatus.PENDING;
        String reason = fullySucceeded
                ? "Request processed successfully"
                : results.stream()
                .filter(r -> !"SUCCESS".equals(r.getNewStatus()))
                .map(RetryResponse.LegRetryResult::getReason)
                .filter(r -> r != null && !r.isBlank())
                .reduce((first, second) -> second)
                .orElse("One or more legs failed after retry");

        posting.setStatus(newStatus);
        posting.setReason(reason);
        postingRepository.save(posting);
        log.info("Retry finished | postingId={} legsProcessed={} postingStatus={}",
                postingId, results.size(), newStatus);

        if (fullySucceeded && eventPublisher != null) {
            eventPublisher.publishSuccess(new PostingSuccessEvent(
                    posting.getPostingId(),
                    posting.getEndToEndReferenceId(),
                    posting.getRequestType(),
                    posting.getTargetSystems(),
                    Instant.now()
            ));
        }

        return results;
    }

    private AccountPostingRequest deserializeRequest(String requestPayload) {
        if (requestPayload == null) return null;
        try {
            return objectMapper.readValue(requestPayload, AccountPostingRequest.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize requestPayload", ex);
            return null;
        }
    }
}
