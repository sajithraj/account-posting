package com.accountposting.service.impl;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountposting.AccountPostingSearchRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegCreateResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.AccountPosting;
import com.accountposting.entity.PostingConfig;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.mapper.AccountPostingMapper;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.repository.AccountPostingSpecification;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.AccountPostingLegService;
import com.accountposting.service.AccountPostingRequestValidator;
import com.accountposting.service.AccountPostingService;
import com.accountposting.service.strategy.PostingStrategy;
import com.accountposting.service.strategy.PostingStrategyFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPostingServiceImpl implements AccountPostingService {

    private final AccountPostingRepository repository;
    private final AccountPostingMapper mapper;
    private final AccountPostingLegMapper legMapper;
    private final AccountPostingLegService legService;
    private final PostingConfigRepository postingConfigRepository;
    private final PostingStrategyFactory strategyFactory;
    private final com.accountposting.service.PostingRetryProcessor retryProcessor;
    private final AccountPostingRequestValidator requestValidator;
    private final ObjectMapper objectMapper;
    @Qualifier("retryExecutor")
    private final Executor retryExecutor;
    private final PlatformTransactionManager transactionManager;

    /**
     * Null when app.kafka.enabled=false — publishing is skipped silently.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostingEventPublisher eventPublisher;

    // noRollbackFor: pre-leg failures (e.g. no config) persist the FAILED status before throwing
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AccountPostingCreateResponse create(AccountPostingRequest request) {
        MDC.put("e2eRef", request.getEndToEndReferenceId());
        MDC.put("requestType", request.getRequestType());
        try {
            return doCreate(request);
        } finally {
            MDC.remove("e2eRef");
            MDC.remove("requestType");
            MDC.remove("postingId");
        }
    }

    private AccountPostingCreateResponse doCreate(AccountPostingRequest request) {
        // Phase 1: structural validation before any DB operation
        requestValidator.validateFields(request);

        if (repository.existsByEndToEndReferenceId(request.getEndToEndReferenceId())) {
            throw new BusinessException("DUPLICATE_E2E_REF",
                    "Posting already exists for endToEndReferenceId: "
                            + request.getEndToEndReferenceId());
        }

        // 1. Persist the request immediately (PENDING) — stored regardless of what fails next,
        //    giving full audit visibility even for invalid / misconfigured requests.
        AccountPosting posting = mapper.toEntity(request);
        posting.setRequestPayload(toJson(request));
        posting = repository.save(posting);
        MDC.put("postingId", String.valueOf(posting.getPostingId()));
        log.info("Posting saved | sourceName={} requestType={}", request.getSourceName(), request.getRequestType());

        // 2. Validate enum fields — invalid values → FAILED + 400.
        //    Posting is already in DB so the caller can query it regardless of the outcome.
        try {
            requestValidator.validateEnums(request.getSourceName(), request.getRequestType());
        } catch (BusinessException ex) {
            posting.setStatus(PostingStatus.FAILED);
            posting.setReason(ex.getMessage());
            posting.setResponsePayload(toJson(Map.of("error", ex.getMessage())));
            repository.save(posting);
            throw ex;
        }

        // 3. Load config — derive targetSystems
        List<PostingConfig> configs = postingConfigRepository
                .findByRequestTypeOrderByOrderSeqAsc(request.getRequestType());

        if (configs.isEmpty()) {
            String noConfigReason = "No posting config found for requestType: " + request.getRequestType();
            posting.setStatus(PostingStatus.FAILED);
            posting.setReason(noConfigReason);
            posting.setResponsePayload(toJson(Map.of("error", noConfigReason)));
            repository.save(posting);
            throw new BusinessException("NO_CONFIG_FOUND", noConfigReason);
        }

        String targetSystems = configs.stream()
                .map(PostingConfig::getTargetSystem)
                .collect(Collectors.joining("_"));
        posting.setTargetSystems(targetSystems);
        repository.save(posting);
        log.info("Config loaded | targetSystems={}", targetSystems);

        // 3. Pre-insert ALL legs as PENDING before calling any external system.
        //    This ensures every leg row exists in the DB even if the first external call fails,
        //    so that a subsequent retry can find and re-process all legs.
        List<AccountPostingLegResponse> preInsertedLegs = new ArrayList<>();
        for (PostingConfig config : configs) {
            AccountPostingLegRequest legReq = legMapper.toCreateLegRequest(
                    request, config.getOrderSeq(), config.getTargetSystem(),
                    LegMode.NORM, config.getOperation(), null);
            AccountPostingLegResponse pre = legService.addLeg(posting.getPostingId(), legReq);
            preInsertedLegs.add(pre);
            log.info("Pre-inserted PENDING leg#{} targetSystem={}", pre.getPostingLegId(), config.getTargetSystem());
        }

        // 4. Execute each strategy sequentially, passing the pre-inserted leg ID.
        //    Strategies skip their own INSERT because existingLegId is non-null.
        List<LegResponse> legResponses = new ArrayList<>();
        boolean allSuccess = true;

        for (AccountPostingLegResponse pre : preInsertedLegs) {
            try {
                PostingStrategy strategy = strategyFactory.resolve(
                        pre.getTargetSystem() + "_" + pre.getOperation());
                LegResponse legResponse = strategy.process(
                        posting.getPostingId(), pre.getLegOrder(), request, false, pre.getPostingLegId());
                legResponses.add(legResponse);
                if (!"SUCCESS".equalsIgnoreCase(legResponse.getStatus())) {
                    allSuccess = false;
                }
            } catch (Exception ex) {
                log.error("Strategy failed | targetSystem={}", pre.getTargetSystem(), ex);
                allSuccess = false;
            }
        }

        // 5. Persist final posting status + reason
        PostingStatus finalStatus = allSuccess ? PostingStatus.SUCCESS : PostingStatus.PENDING;
        String reason = allSuccess
                ? "Request processed successfully"
                : legResponses.stream()
                .filter(l -> !"SUCCESS".equalsIgnoreCase(l.getStatus()))
                .map(LegResponse::getReason)
                .filter(r -> r != null && !r.isBlank())
                .reduce((first, second) -> second)
                .orElse("One or more legs failed");
        posting.setStatus(finalStatus);
        posting.setReason(reason);
        posting.setResponsePayload(toJson(Map.of(
                "status", finalStatus.name(),
                "legs", legResponses)));
        repository.save(posting);
        log.info("Posting completed | status={} legs={}", finalStatus, legResponses.size());

        // 6. Publish success event — downstream consumers process only when all legs succeed
        if (finalStatus == PostingStatus.SUCCESS && eventPublisher != null) {
            eventPublisher.publishSuccess(new PostingSuccessEvent(
                    posting.getPostingId(),
                    posting.getEndToEndReferenceId(),
                    posting.getRequestType(),
                    posting.getTargetSystems(),
                    Instant.now()
            ));
        }

        List<LegCreateResponse> legCreateResponses = legResponses.stream()
                .map(leg -> {
                    LegCreateResponse r = new LegCreateResponse();
                    r.setName(leg.getName());
                    r.setType(leg.getType());
                    r.setAccount(leg.getAccount());
                    r.setReferenceId(leg.getReferenceId());
                    r.setPostedTime(leg.getPostedTime());
                    r.setStatus(leg.getStatus());
                    r.setReason(leg.getReason());
                    return r;
                })
                .toList();

        AccountPostingCreateResponse response = mapper.toCreateResponse(posting);
        response.setProcessedAt(Instant.now());
        response.setResponses(legCreateResponses);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingResponse> search(AccountPostingSearchRequest criteria, Pageable pageable) {
        return repository
                .findAll(AccountPostingSpecification.from(criteria), pageable)
                .map(posting -> {
                    AccountPostingResponse resp = mapper.toResponse(posting);
                    resp.setResponses(fetchLegs(posting.getPostingId()));
                    return resp;
                });
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingResponse findById(Long postingId) {
        AccountPosting posting = getOrThrow(postingId);
        AccountPostingResponse response = mapper.toResponse(posting);
        response.setResponses(fetchLegs(postingId));
        return response;
    }

    /**
     * Lock TTL: 2 minutes — prevents concurrent retries of the same posting.
     */
    private static final int LOCK_TTL_SECONDS = 120;

    /**
     * Retries PENDING postings (or a specific subset) in parallel.
     * <p>
     * NOT @Transactional — the lock is committed in its own short transaction via TransactionTemplate
     * before futures are dispatched. This prevents a deadlock where the outer transaction holds a row
     * lock on the posting while waiting for futures that need to UPDATE that same row.
     * <p>
     * 1. Determine candidate postingIds
     * 2. Lock them atomically in a committed transaction
     * 3. Dispatch one CompletableFuture per locked posting (each has its own transaction)
     * 4. Aggregate results
     */
    @Override
    public RetryResponse retry(RetryRequest request) {
        MDC.put("operation", "RETRY");
        try {
            return doRetry(request);
        } finally {
            MDC.remove("operation");
        }
    }

    private RetryResponse doRetry(RetryRequest request) {
        Instant now = Instant.now();
        Instant lockUntil = now.plusSeconds(LOCK_TTL_SECONDS);

        // 1. Determine candidate IDs
        List<Long> candidateIds = CollectionUtils.isEmpty(request.getPostingIds())
                ? repository.findEligibleForRetry(PostingStatus.PENDING, now)
                .stream().map(AccountPosting::getPostingId).toList()
                : request.getPostingIds();

        log.info("RETRY START | candidates={} ids={}", candidateIds.size(), candidateIds);

        if (candidateIds.isEmpty()) {
            log.info("RETRY | No eligible postings — nothing to retry");
            return RetryResponse.builder().totalLegsRetried(0).successCount(0).failedCount(0)
                    .results(List.of()).build();
        }

        // 2. Lock + fetch in one short transaction that commits immediately.
        //    After execute() returns, the lock is visible in the DB and no TX is held.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<AccountPosting> lockedPostings = txTemplate.execute(tx -> {
            int lockedCount = repository.lockForRetry(candidateIds, PostingStatus.PENDING, now, lockUntil);
            log.info("RETRY | lockForRetry locked={} lockUntil={}", lockedCount, lockUntil);
            return repository.findByIdsAndLockUntil(candidateIds, lockUntil);
        });

        if (lockedPostings == null || lockedPostings.isEmpty()) {
            log.warn("RETRY | No postings locked — all already in progress or not PENDING");
            return RetryResponse.builder().totalLegsRetried(0).successCount(0).failedCount(0)
                    .results(List.of()).build();
        }

        log.info("RETRY | Locked postings={} ids={}",
                lockedPostings.size(),
                lockedPostings.stream().map(AccountPosting::getPostingId).toList());

        // 3. Capture MDC so each async thread inherits the same traceId + operation
        final Map<String, String> parentMdc = java.util.Optional
                .ofNullable(MDC.getCopyOfContextMap()).orElse(Map.of());

        // 4. Dispatch one future per posting — no outer TX held, each future owns its own TX
        List<Long> lockedIds = lockedPostings.stream().map(AccountPosting::getPostingId).toList();
        List<CompletableFuture<List<RetryResponse.LegRetryResult>>> futures = lockedIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    MDC.setContextMap(parentMdc);
                    MDC.put("postingId", String.valueOf(id));
                    try {
                        return retryProcessor.process(id);
                    } finally {
                        MDC.clear();
                    }
                }, retryExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("RETRY | All futures completed — aggregating");

        // 5. Aggregate
        List<RetryResponse.LegRetryResult> allResults = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < futures.size(); i++) {
            Long postingId = lockedIds.get(i);
            try {
                List<RetryResponse.LegRetryResult> legResults = futures.get(i).get();
                log.info("RETRY | postingId={} legResults={} outcomes={}",
                        postingId, legResults.size(),
                        legResults.stream().map(r -> r.getPreviousStatus() + "→" + r.getNewStatus()).toList());
                allResults.addAll(legResults);
                for (RetryResponse.LegRetryResult r : legResults) {
                    if ("SUCCESS".equals(r.getNewStatus())) successCount++;
                    else failedCount++;
                }
            } catch (Exception ex) {
                log.error("RETRY | future failed | postingId={}", postingId, ex);
            }
        }

        log.info("RETRY DONE | totalLegs={} success={} failed={}", allResults.size(), successCount, failedCount);
        return RetryResponse.builder()
                .totalLegsRetried(allResults.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .results(allResults)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getTargetSystems(String q) {
        return repository.findDistinctTargetSystems(q);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<LegResponse> fetchLegs(Long postingId) {
        return legService.listLegs(postingId).stream()
                .map(mapper::toLegResponse)
                .toList();
    }

    private AccountPosting getOrThrow(Long postingId) {
        return repository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountPosting", postingId));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise object to JSON", ex);
            return "{}";
        }
    }
}
