package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.PostingSearchResponse;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import com.sr.accountposting.repository.posting.AccountPostingRepository;
import com.sr.accountposting.util.AppConfig;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class AccountPostingServiceImpl implements AccountPostingService {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingServiceImpl.class);

    private final AccountPostingRepository postingRepo;
    private final AccountPostingLegRepository legRepo;
    private final SqsClient sqsClient;

    @Inject
    public AccountPostingServiceImpl(AccountPostingRepository postingRepo,
                                     AccountPostingLegRepository legRepo,
                                     SqsClient sqsClient) {
        this.postingRepo = postingRepo;
        this.legRepo = legRepo;
        this.sqsClient = sqsClient;
    }

    @Override
    public PostingResponse findById(String postingId) {
        log.info("findById postingId={}", postingId);
        AccountPostingEntity posting = postingRepo.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));
        List<AccountPostingLegEntity> legs = legRepo.findByPostingId(postingId);
        log.info("findById postingId={} status={} legCount={}", postingId, posting.getStatus(), legs.size());
        return toResponse(posting, legs);
    }

    @Override
    public PostingSearchResponse search(PostingSearchRequest req) {
        int limit = req.getLimit() != null ? req.getLimit() : 20;
        if (limit < 1 || limit > 200) {
            throw new ValidationException("INVALID_LIMIT", "limit must be between 1 and 200");
        }
        if (!hasSearchCriteria(req) && req.getPageToken() == null) {
            Instant now = Instant.now();
            req.setFromDate(now.minus(3, ChronoUnit.DAYS).toString());
            req.setToDate(now.toString());
        }
        log.info("search status={} sourceName={} requestType={} e2eRef={} sourceRef={} fromDate={} toDate={} limit={} pageTokenPresent={}",
                req.getStatus(), req.getSourceName(), req.getRequestType(), req.getEndToEndReferenceId(),
                req.getSourceReferenceId(), req.getFromDate(), req.getToDate(), limit, req.getPageToken() != null);
        AccountPostingRepository.SearchResult postings = postingRepo.search(
                req.getStatus(), req.getSourceName(), req.getRequestType(),
                req.getEndToEndReferenceId(), req.getSourceReferenceId(),
                req.getFromDate(), req.getToDate(),
                limit, req.getPageToken());

        List<PostingResponse> results = postings.getItems().stream()
                .map(p -> toResponse(p, legRepo.findByPostingId(p.getPostingId())))
                .collect(Collectors.toList());
        log.info("search returned {} results nextPageTokenPresent={}",
                results.size(), postings.getNextPageToken() != null);
        return PostingSearchResponse.builder()
                .items(results)
                .nextPageToken(postings.getNextPageToken())
                .build();
    }

    private boolean hasSearchCriteria(PostingSearchRequest req) {
        return req.getStatus() != null || req.getSourceName() != null || req.getRequestType() != null
                || req.getEndToEndReferenceId() != null || req.getSourceReferenceId() != null
                || req.getFromDate() != null || req.getToDate() != null;
    }

    @Override
    public RetryResponse retry(RetryRequest request) {
        if (request == null || request.getPostingIds() == null || request.getPostingIds().isEmpty()) {
            throw new ValidationException("POSTING_IDS_REQUIRED",
                    "posting_ids must be provided for retry; bulk retry is not allowed");
        }

        List<AccountPostingEntity> candidates = new ArrayList<>();
        int totalRequested = request.getPostingIds().size();
        int skipped = 0;

        for (String id : request.getPostingIds()) {
            if (id == null || id.isBlank()) {
                throw new ValidationException("INVALID_POSTING_ID", "posting_ids must not contain blank values");
            }
            java.util.Optional<AccountPostingEntity> posting = postingRepo.findById(id);
            if (posting.isPresent()) {
                candidates.add(posting.get());
            } else {
                log.warn("Posting not found for retry postingId={}", id);
                skipped++;
            }
        }

        int queued = 0;
        long now = System.currentTimeMillis();

        for (AccountPostingEntity posting : candidates) {
            if (!isRetryEligible(posting, now)) {
                skipped++;
                continue;
            }

            IncomingPostingRequest originalRequest =
                    JsonUtil.fromJson(posting.getRequestPayload(), IncomingPostingRequest.class);

            PostingJob job = PostingJob.builder()
                    .postingId(posting.getPostingId())
                    .requestPayload(originalRequest)
                    .requestMode(RequestMode.RETRY)
                    .build();
            publishJob(job);
            postingRepo.updateStatus(posting.getPostingId(), PostingStatus.RTRY.name());
            queued++;
        }

        log.info("Retry request by '{}' - {} requested, {} found, {} queued for processing, {} skipped",
                request.getRequestedBy(), totalRequested, candidates.size(), queued, skipped);

        return RetryResponse.builder()
                .totalPostings(totalRequested)
                .queued(queued)
                .skippedLocked(skipped)
                .message("Retry processing submitted. Check dashboard for status updates.")
                .build();
    }

    private boolean isRetryEligible(AccountPostingEntity posting, long now) {
        boolean eligibleStatus = PostingStatus.PNDG.name().equals(posting.getStatus())
                || PostingStatus.RCVD.name().equals(posting.getStatus());
        Long lockedUntil = posting.getRetryLockedUntil();
        return eligibleStatus && (lockedUntil == null || lockedUntil < now);
    }

    private void publishJob(PostingJob job) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(AppConfig.QUEUE_URL)
                .messageBody(JsonUtil.toJson(job))
                .build());
        log.info("Posting [id={}] queued for {} processing", job.getPostingId(), job.getRequestMode());
    }

    private PostingResponse toResponse(AccountPostingEntity p, List<AccountPostingLegEntity> legs) {
        return PostingResponse.builder()
                .postingId(p.getPostingId())
                .sourceReferenceId(p.getSourceReferenceId())
                .endToEndReferenceId(p.getEndToEndReferenceId())
                .sourceName(p.getSourceName())
                .requestType(p.getRequestType())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .creditDebitIndicator(p.getCreditDebitIndicator())
                .debtorAccount(p.getDebtorAccount())
                .creditorAccount(p.getCreditorAccount())
                .requestedExecutionDate(p.getRequestedExecutionDate())
                .remittanceInformation(p.getRemittanceInformation())
                .postingStatus(p.getStatus())
                .targetSystems(p.getTargetSystems())
                .reason(p.getReason())
                .processedAt(p.getUpdatedAt())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .legs(legs.stream().map(this::toLegResponse).collect(Collectors.toList()))
                .build();
    }

    private LegResponse toLegResponse(AccountPostingLegEntity leg) {
        return LegResponse.builder()
                .postingId(leg.getPostingId())
                .transactionId(leg.getTransactionId())
                .transactionOrder(leg.getTransactionOrder())
                .targetSystem(leg.getTargetSystem())
                .account(leg.getAccount())
                .status(leg.getStatus())
                .referenceId(leg.getReferenceId())
                .reason(leg.getReason())
                .attemptNumber(leg.getAttemptNumber())
                .postedTime(leg.getPostedTime())
                .mode(leg.getMode())
                .operation(leg.getOperation())
                .createdAt(leg.getCreatedAt())
                .updatedAt(leg.getUpdatedAt())
                .build();
    }
}
