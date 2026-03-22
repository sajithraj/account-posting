package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.AccountPosting;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.service.strategy.PostingStrategy;
import com.accountposting.service.strategy.PostingStrategyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the retry of a single posting in its own transaction.
 * Called from a parallel CompletableFuture so each posting is processed independently.
 * <p>
 * Flow:
 * 1. Load posting + deserialize original request
 * 2. Fetch non-SUCCESS legs ordered by legOrder
 * 3. Execute each leg sequentially via the resolved strategy (isRetry=true)
 * 4. Re-evaluate and persist posting status
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingRetryProcessor {

    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegService legService;
    private final PostingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;

    /**
     * Null when app.kafka.enabled=false — publishing is skipped silently.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostingEventPublisher eventPublisher;

    @Transactional
    public List<RetryResponse.LegRetryResult> process(Long postingId) {
        // Ensure postingId is in MDC (may already be set by the caller's async lambda)
        MDC.put("postingId", String.valueOf(postingId));

        log.info("RETRY-PROCESSOR START | thread={}", Thread.currentThread().getName());

        // 1. Load posting and deserialize original request
        AccountPosting posting = postingRepository.findById(postingId).orElse(null);
        if (posting == null) {
            log.warn("RETRY-PROCESSOR | posting not found — skipping");
            return List.of();
        }
        MDC.put("e2eRef", posting.getEndToEndReferenceId());
        MDC.put("requestType", posting.getRequestType());
        log.info("RETRY-PROCESSOR | status={}", posting.getStatus());

        AccountPostingRequest originalRequest = deserializeRequest(posting.getRequestPayload());
        if (originalRequest == null) {
            log.warn("RETRY-PROCESSOR | requestPayload missing or unreadable — skipping");
            return List.of();
        }

        // 2. Fetch non-SUCCESS legs ordered by legOrder
        List<AccountPostingLegResponse> pendingLegs = legService.listNonSuccessLegs(postingId);
        log.info("RETRY-PROCESSOR | non-SUCCESS legs={} targets={}",
                pendingLegs.size(),
                pendingLegs.stream().map(l -> l.getTargetSystem() + "#" + l.getLegOrder()
                        + "=" + l.getStatus()).toList());

        if (pendingLegs.isEmpty()) {
            log.info("RETRY-PROCESSOR | no legs to retry");
            return List.of();
        }

        // 3. Execute each leg sequentially in legOrder using the resolved strategy
        List<RetryResponse.LegRetryResult> results = new ArrayList<>();
        for (AccountPostingLegResponse leg : pendingLegs) {
            log.info("RETRY-PROCESSOR | processing leg#{} order={} target={}",
                    leg.getPostingLegId(), leg.getLegOrder(), leg.getTargetSystem());
            try {
                PostingStrategy strategy = strategyFactory.resolve(
                        leg.getTargetSystem() + "_" + leg.getOperation());
                LegResponse legResult = strategy.process(
                        postingId, leg.getLegOrder(), originalRequest, true, leg.getPostingLegId());

                log.info("RETRY-PROCESSOR | leg#{} {} → {}", leg.getPostingLegId(),
                        leg.getStatus(), legResult.getStatus());

                results.add(RetryResponse.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(legResult.getStatus())
                        .reason(legResult.getReason())
                        .build());
            } catch (Exception ex) {
                log.error("RETRY-PROCESSOR | leg#{} target={} threw exception",
                        leg.getPostingLegId(), leg.getTargetSystem(), ex);
                results.add(RetryResponse.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(LegStatus.FAILED.name())
                        .reason(ex.getMessage())
                        .build());
            }
        }

        // 4. Re-evaluate posting status + reason: SUCCESS only when ALL legs are now SUCCESS
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

        log.info("RETRY-PROCESSOR DONE | processedLegs={} postingStatus={}", results.size(), newStatus);

        // 5. Publish success event when retry brings posting to full SUCCESS
        if (fullySucceeded && eventPublisher != null) {
            eventPublisher.publishSuccess(new PostingSuccessEvent(
                    posting.getPostingId(),
                    posting.getEndToEndReferenceId(),
                    posting.getRequestType(),
                    posting.getTargetSystems(),
                    java.time.Instant.now()
            ));
        }

        return results;
    }

    private AccountPostingRequest deserializeRequest(String requestPayload) {
        if (requestPayload == null) return null;
        try {
            return objectMapper.readValue(requestPayload, AccountPostingRequest.class);
        } catch (Exception ex) {
            log.warn("Could not deserialize requestPayload", ex);
            return null;
        }
    }
}
