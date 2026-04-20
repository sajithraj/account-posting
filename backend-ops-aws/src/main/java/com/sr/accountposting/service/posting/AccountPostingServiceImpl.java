package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.exception.ResourceNotFoundException;
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
    public PostingResponse findById(Long postingId) {
        AccountPostingEntity posting = postingRepo.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));
        List<AccountPostingLegEntity> legs = legRepo.findByPostingId(postingId);
        return toResponse(posting, legs);
    }

    @Override
    public List<PostingResponse> search(PostingSearchRequest req) {
        List<AccountPostingEntity> postings = postingRepo.search(
                req.getStatus(), req.getSourceName(),
                req.getFromDate(), req.getToDate(),
                req.getLimit() != null ? req.getLimit() : 20);

        return postings.stream()
                .map(p -> toResponse(p, legRepo.findByPostingId(p.getPostingId())))
                .collect(Collectors.toList());
    }

    @Override
    public RetryResponse retry(RetryRequest request) {
        List<Long> candidates;

        if (request.getPostingIds() != null && !request.getPostingIds().isEmpty()) {
            candidates = request.getPostingIds();
        } else {
            List<AccountPostingEntity> pndg = postingRepo.findByStatus(PostingStatus.PNDG.name());
            List<AccountPostingEntity> received = postingRepo.findByStatus(PostingStatus.RECEIVED.name());
            candidates = new ArrayList<>();
            pndg.forEach(p -> candidates.add(p.getPostingId()));
            received.forEach(p -> candidates.add(p.getPostingId()));
        }

        int queued = 0;
        int skipped = 0;

        for (Long postingId : candidates) {
            AccountPostingEntity posting = postingRepo.findById(postingId).orElse(null);
            if (posting == null) {
                skipped++;
                continue;
            }

            boolean locked = postingRepo.acquireRetryLock(
                    postingId, System.currentTimeMillis() + AppConfig.RETRY_LOCK_TTL_MS);
            if (!locked) {
                skipped++;
                continue;
            }

            IncomingPostingRequest originalRequest =
                    JsonUtil.fromJson(posting.getRequestPayload(), IncomingPostingRequest.class);

            PostingJob job = PostingJob.builder()
                    .postingId(postingId)
                    .requestPayload(originalRequest)
                    .requestMode(RequestMode.RETRY)
                    .build();
            publishJob(job);
            queued++;
        }

        log.info("Retry request by '{}' — {} candidates, {} queued, {} skipped (lock held)",
                request.getRequestedBy(), candidates.size(), queued, skipped);

        return RetryResponse.builder()
                .totalPostings(candidates.size())
                .queued(queued)
                .skippedLocked(skipped)
                .message("Retry processing submitted. Check dashboard for status updates.")
                .build();
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
                .sourceReferenceId(p.getSourceReferenceId())
                .endToEndReferenceId(p.getEndToEndReferenceId())
                .postingStatus(p.getStatus())
                .processedAt(p.getUpdatedAt())
                .legs(legs.stream().map(this::toLegResponse).collect(Collectors.toList()))
                .build();
    }

    private LegResponse toLegResponse(AccountPostingLegEntity leg) {
        return LegResponse.builder()
                .postingId(leg.getPostingId())
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
