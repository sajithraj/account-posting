package com.accountposting.service.retry;

import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.dto.retry.RetryResponseV2;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
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
public class PostingRetryProcessorV2 {

    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegServiceV2 legService;
    private final PostingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostingEventPublisher eventPublisher;

    @Transactional
    public List<RetryResponseV2.LegRetryResult> process(Long postingId) {
        MDC.put("postingId", String.valueOf(postingId));
        log.info("Retry started | thread={}", Thread.currentThread().getName());

        AccountPostingEntity posting = postingRepository.findById(postingId).orElse(null);
        if (posting == null) {
            log.warn("Posting not found, skipping retry | postingId={}", postingId);
            return List.of();
        }

        MDC.put("e2eRef", posting.getEndToEndReferenceId());
        MDC.put("requestType", posting.getRequestType());

        AccountPostingRequestV2 originalRequest = deserializeRequest(posting.getRequestPayload());
        if (originalRequest == null) {
            log.warn("Original request payload is missing or unreadable, skipping | postingId={}", postingId);
            return List.of();
        }

        List<AccountPostingLegResponseV2> pendingLegs = legService.listNonSuccessLegs(postingId);
        log.info("Non-SUCCESS legs to retry | postingId={} count={} legs={}",
                postingId, pendingLegs.size(),
                pendingLegs.stream().map(l -> l.getTargetSystem() + "#" + l.getLegOrder() + "=" + l.getStatus()).toList());

        if (pendingLegs.isEmpty()) {
            log.info("No legs require retry | postingId={}", postingId);
            return List.of();
        }

        List<RetryResponseV2.LegRetryResult> results = new ArrayList<>();

        for (AccountPostingLegResponseV2 leg : pendingLegs) {
            log.info("Retrying leg | postingId={} postingLegId={} legOrder={} targetSystem={}",
                    postingId, leg.getPostingLegId(), leg.getLegOrder(), leg.getTargetSystem());
            try {
                PostingStrategy strategy = strategyFactory.resolve(leg.getTargetSystem() + "_" + leg.getOperation());
                LegResponseV2 legResult = strategy.process(postingId, leg.getLegOrder(), originalRequest, true, leg.getPostingLegId());

                log.info("Leg retry completed | postingLegId={} status: {} -> {}",
                        leg.getPostingLegId(), leg.getStatus(), legResult.getStatus());

                results.add(RetryResponseV2.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(legResult.getStatus())
                        .reason(legResult.getReason())
                        .build());
            } catch (Exception ex) {
                log.error("Leg retry failed with exception | postingId={} postingLegId={} targetSystem={}",
                        postingId, leg.getPostingLegId(), leg.getTargetSystem(), ex);
                results.add(RetryResponseV2.LegRetryResult.builder()
                        .postingLegId(leg.getPostingLegId())
                        .postingId(postingId)
                        .previousStatus(leg.getStatus().name())
                        .newStatus(LegStatus.FAILED.name())
                        .reason(ex.getMessage())
                        .build());
            }
        }

        // Promote posting to SUCCESS only when every leg across all attempts is now SUCCESS.
        List<AccountPostingLegResponseV2> allLegs = legService.listLegs(postingId);
        boolean fullySucceeded = allLegs.stream().allMatch(l -> l.getStatus() == LegStatus.SUCCESS);
        PostingStatus newStatus = fullySucceeded ? PostingStatus.ACSP : PostingStatus.PNDG;
        String reason = fullySucceeded
                ? "Request processed successfully"
                : results.stream()
                .filter(r -> !"SUCCESS".equals(r.getNewStatus()))
                .map(RetryResponseV2.LegRetryResult::getReason)
                .filter(r -> r != null && !r.isBlank())
                .reduce((first, second) -> second)
                .orElse("One or more legs failed after retry");

        posting.setStatus(newStatus);
        posting.setReason(reason);
        posting.setRetryLockedUntil(null); // release lock so the posting is immediately retryable again
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

    private AccountPostingRequestV2 deserializeRequest(String requestPayload) {
        if (requestPayload == null) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(requestPayload);
            // H2 JSONB columns wrap the stored string in outer JSON quotes (double-encoding).
            // When that happens the root token is a STRING whose text value is the real JSON.
            String json = node.isTextual() ? node.textValue() : requestPayload;
            return objectMapper.readValue(json, AccountPostingRequestV2.class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize requestPayload", ex);
            return null;
        }
    }
}
