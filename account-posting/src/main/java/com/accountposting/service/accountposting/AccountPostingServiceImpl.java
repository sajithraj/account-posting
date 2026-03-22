package com.accountposting.service.accountposting;

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
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.PostingConfig;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.mapper.AccountPostingMapper;
import com.accountposting.mapper.MappingUtils;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.repository.AccountPostingSpecification;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.AccountPostingRequestValidator;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegService;
import com.accountposting.service.retry.PostingRetryProcessor;
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
import java.util.Comparator;
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
    private final PostingRetryProcessor retryProcessor;
    private final AccountPostingRequestValidator requestValidator;
    private final MappingUtils mappingUtils;
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
        log.info("CREATE REQUEST | {}", mappingUtils.toJson(request));

        if (repository.existsByEndToEndReferenceId(request.getEndToEndReferenceId())) {
            log.warn("Duplicate posting rejected | e2eRef={}", request.getEndToEndReferenceId());
            throw new BusinessException("DUPLICATE_E2E_REF",
                    "Posting already exists for endToEndReferenceId: " + request.getEndToEndReferenceId());
        }

        // Save an initial PENDING record first so validation failures are always auditable in the DB.
        AccountPostingEntity posting = mapper.toEntity(request);
        posting.setRequestPayload(mappingUtils.toJson(request));
        posting = repository.save(posting);
        MDC.put("postingId", String.valueOf(posting.getPostingId()));
        log.info("Initial posting persisted | postingId={} status={}", posting.getPostingId(), posting.getStatus());

        try {
            requestValidator.validate(request);
        } catch (BusinessException ex) {
            posting.setStatus(PostingStatus.FAILED);
            posting.setReason(ex.getMessage());
            posting.setResponsePayload(mappingUtils.toJson(Map.of("error", ex.getMessage())));
            repository.save(posting);
            log.warn("Posting marked FAILED due to validation | postingId={} reason={}", posting.getPostingId(), ex.getMessage());
            throw ex;
        }

        // Load routing config ordered by execution sequence.
        List<PostingConfig> configs = postingConfigRepository
                .findByRequestTypeOrderByOrderSeqAsc(request.getRequestType())
                .stream()
                .sorted(Comparator.comparingInt(PostingConfig::getOrderSeq))
                .toList();
        log.info("Routing config loaded | postingId={} requestType={} configs={}",
                posting.getPostingId(), request.getRequestType(), mappingUtils.toJson(configs));

        if (configs.isEmpty()) {
            String reason = "No posting config found for requestType: " + request.getRequestType();
            posting.setStatus(PostingStatus.FAILED);
            posting.setReason(reason);
            posting.setResponsePayload(mappingUtils.toJson(Map.of("error", reason)));
            repository.save(posting);
            log.warn("Posting marked FAILED — no config found | postingId={} requestType={}",
                    posting.getPostingId(), request.getRequestType());
            throw new BusinessException("NO_CONFIG_FOUND", reason);
        }

        String targetSystems = configs.stream()
                .map(PostingConfig::getTargetSystem)
                .collect(Collectors.joining("_"));
        posting.setTargetSystems(targetSystems);
        repository.save(posting);
        log.info("Target systems recorded | postingId={} targetSystems={}", posting.getPostingId(), targetSystems);

        // Pre-insert all legs as PENDING before calling any external system.
        // This guarantees every leg is visible and retryable even if an earlier call fails.
        List<AccountPostingLegResponse> preInsertedLegs = new ArrayList<>();
        for (PostingConfig config : configs) {
            AccountPostingLegRequest legReq = legMapper.toCreateLegRequest(
                    request, config.getOrderSeq(), config.getTargetSystem(),
                    LegMode.NORM, config.getOperation(), null);
            AccountPostingLegResponse pre = legService.addLeg(posting.getPostingId(), legReq);
            preInsertedLegs.add(pre);
            log.info("Leg pre-inserted | postingId={} postingLegId={} targetSystem={} legOrder={}",
                    posting.getPostingId(), pre.getPostingLegId(), pre.getTargetSystem(), pre.getLegOrder());
        }

        List<LegResponse> legResponses = new ArrayList<>();
        boolean allSuccess = true;

        for (AccountPostingLegResponse pre : preInsertedLegs) {
            try {
                log.info("Invoking strategy | postingId={} postingLegId={} targetSystem={} operation={}",
                        posting.getPostingId(), pre.getPostingLegId(), pre.getTargetSystem(), pre.getOperation());
                PostingStrategy strategy = strategyFactory.resolve(pre.getTargetSystem() + "_" + pre.getOperation());
                LegResponse legResponse = strategy.process(
                        posting.getPostingId(), pre.getLegOrder(), request, false, pre.getPostingLegId());
                legResponses.add(legResponse);
                log.info("Strategy completed | postingLegId={} targetSystem={} status={} referenceId={}",
                        legResponse.getPostingLegId(), legResponse.getName(), legResponse.getStatus(), legResponse.getReferenceId());
                if (!"SUCCESS".equalsIgnoreCase(legResponse.getStatus())) {
                    allSuccess = false;
                }
            } catch (Exception ex) {
                log.error("Strategy execution failed | postingId={} targetSystem={}",
                        posting.getPostingId(), pre.getTargetSystem(), ex);
                allSuccess = false;
            }
        }

        PostingStatus finalStatus = allSuccess ? PostingStatus.SUCCESS : PostingStatus.PENDING;
        String finalReason = allSuccess
                ? "Request processed successfully"
                : legResponses.stream()
                .filter(l -> !"SUCCESS".equalsIgnoreCase(l.getStatus()))
                .map(LegResponse::getReason)
                .filter(r -> r != null && !r.isBlank())
                .reduce((first, second) -> second)
                .orElse("One or more legs failed");
        posting.setStatus(finalStatus);
        posting.setReason(finalReason);
        posting.setResponsePayload(mappingUtils.toJson(Map.of("status", finalStatus.name(), "legs", legResponses)));
        repository.save(posting);
        log.info("Posting finalized | postingId={} status={} legs={}", posting.getPostingId(), finalStatus, legResponses.size());

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

        log.info("CREATE RESPONSE | {}", mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingResponse> search(AccountPostingSearchRequest criteria, Pageable pageable) {
        log.info("SEARCH REQUEST | {}", mappingUtils.toJson(criteria));
        Page<AccountPostingResponse> result = repository
                .findAll(AccountPostingSpecification.from(criteria), pageable)
                .map(posting -> {
                    AccountPostingResponse resp = mapper.toResponse(posting);
                    resp.setResponses(fetchLegs(posting.getPostingId()));
                    return resp;
                });
        log.info("SEARCH RESPONSE | totalElements={} totalPages={}", result.getTotalElements(), result.getTotalPages());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingResponse findById(Long postingId) {
        log.info("FIND BY ID REQUEST | postingId={}", postingId);
        AccountPostingEntity posting = getOrThrow(postingId);
        AccountPostingResponse response = mapper.toResponse(posting);
        response.setResponses(fetchLegs(postingId));
        log.info("FIND BY ID RESPONSE | {}", mappingUtils.toJson(response));
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
        log.info("RETRY REQUEST | {}", mappingUtils.toJson(request));
        MDC.put("operation", "RETRY");
        try {
            RetryResponse response = doRetry(request);
            log.info("RETRY RESPONSE | {}", mappingUtils.toJson(response));
            return response;
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
                .stream().map(AccountPostingEntity::getPostingId).toList()
                : request.getPostingIds();

        log.info("RETRY START | candidates={} ids={}", candidateIds.size(), candidateIds);

        if (candidateIds.isEmpty()) {
            log.info("RETRY | No eligible postings — nothing to retry");
            return RetryResponse.builder().totalLegsRetried(0).successCount(0).failedCount(0)
                    .results(List.of()).build();
        }

        // 2. Lock + fetch in one short transaction that commits immediately.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<AccountPostingEntity> lockedPostings = txTemplate.execute(tx -> {
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
                lockedPostings.stream().map(AccountPostingEntity::getPostingId).toList());

        // 3. Capture MDC so each async thread inherits the same traceId + operation
        final Map<String, String> parentMdc = java.util.Optional
                .ofNullable(MDC.getCopyOfContextMap()).orElse(Map.of());

        // 4. Dispatch one future per posting — no outer TX held, each future owns its own TX
        List<Long> lockedIds = lockedPostings.stream().map(AccountPostingEntity::getPostingId).toList();
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

    // ── private helpers ───────────────────────────────────────────────────────

    private List<LegResponse> fetchLegs(Long postingId) {
        return legService.listLegs(postingId).stream()
                .map(mapper::toLegResponse)
                .toList();
    }

    private AccountPostingEntity getOrThrow(Long postingId) {
        return repository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountPostingEntity", postingId));
    }
}
