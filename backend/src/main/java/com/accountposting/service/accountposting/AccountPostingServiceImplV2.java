package com.accountposting.service.accountposting;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountposting.AccountPostingResponseV2;
import com.accountposting.dto.accountposting.AccountPostingSearchRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegCreateResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.dto.retry.RetryRequestV2;
import com.accountposting.dto.retry.RetryResponseV2;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.AccountPostingHistoryEntity;
import com.accountposting.entity.PostingConfig;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.event.PostingSuccessEvent;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.AccountPostingLegMapperV2;
import com.accountposting.mapper.AccountPostingMapperV2;
import com.accountposting.mapper.MappingUtilsV2;
import com.accountposting.repository.AccountPostingHistoryRepository;
import com.accountposting.repository.AccountPostingHistorySpecification;
import com.accountposting.repository.AccountPostingLegHistoryRepository;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.repository.AccountPostingSpecification;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.AccountPostingRequestValidatorV2;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import com.accountposting.service.retry.PostingRetryProcessorV2;
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
public class AccountPostingServiceImplV2 implements AccountPostingServiceV2 {

    private final AccountPostingRepository repository;
    private final AccountPostingHistoryRepository historyRepository;
    private final AccountPostingLegHistoryRepository legHistoryRepository;
    private final AccountPostingMapperV2 mapper;
    private final AccountPostingLegMapperV2 legMapper;
    private final AccountPostingLegServiceV2 legService;
    private final PostingConfigRepository postingConfigRepository;
    private final PostingStrategyFactory strategyFactory;
    private final PostingRetryProcessorV2 retryProcessor;
    private final AccountPostingRequestValidatorV2 requestValidator;
    private final MappingUtilsV2 mappingUtils;
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
    public AccountPostingCreateResponseV2 create(AccountPostingRequestV2 request) {
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

    private AccountPostingCreateResponseV2 doCreate(AccountPostingRequestV2 request) {
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
            posting.setStatus(PostingStatus.RJCT);
            posting.setReason(ex.getMessage());
            posting.setResponsePayload(mappingUtils.toJson(Map.of("error", ex.getMessage())));
            repository.save(posting);
            log.warn("Posting marked RJCT due to validation | postingId={} reason={}", posting.getPostingId(), ex.getMessage());
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
            posting.setStatus(PostingStatus.RJCT);
            posting.setReason(reason);
            posting.setResponsePayload(mappingUtils.toJson(Map.of("error", reason)));
            repository.save(posting);
            log.warn("Posting marked RJCT — no config found | postingId={} requestType={}",
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
        List<AccountPostingLegResponseV2> preInsertedLegs = new ArrayList<>();
        for (PostingConfig config : configs) {
            AccountPostingLegRequestV2 legReq = legMapper.toCreateLegRequest(
                    request, config.getOrderSeq(), config.getTargetSystem(),
                    LegMode.NORM, config.getOperation(), null);
            AccountPostingLegResponseV2 pre = legService.addLeg(posting.getPostingId(), legReq);
            preInsertedLegs.add(pre);
            log.info("Leg pre-inserted | postingId={} postingLegId={} targetSystem={} legOrder={}",
                    posting.getPostingId(), pre.getPostingLegId(), pre.getTargetSystem(), pre.getLegOrder());
        }

        List<LegResponseV2> legResponses = new ArrayList<>();
        boolean allSuccess = true;

        for (AccountPostingLegResponseV2 pre : preInsertedLegs) {
            try {
                log.info("Invoking strategy | postingId={} postingLegId={} targetSystem={} operation={}",
                        posting.getPostingId(), pre.getPostingLegId(), pre.getTargetSystem(), pre.getOperation());
                PostingStrategy strategy = strategyFactory.resolve(pre.getTargetSystem() + "_" + pre.getOperation());
                LegResponseV2 legResponse = strategy.process(
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

        PostingStatus finalStatus = allSuccess ? PostingStatus.ACSP : PostingStatus.PNDG;
        String finalReason = allSuccess
                ? "Request processed successfully"
                : legResponses.stream()
                .filter(l -> !"SUCCESS".equalsIgnoreCase(l.getStatus()))
                .map(LegResponseV2::getReason)
                .filter(r -> r != null && !r.isBlank())
                .reduce((first, second) -> second)
                .orElse("One or more legs failed");
        posting.setStatus(finalStatus);
        posting.setReason(finalReason);
        posting.setResponsePayload(mappingUtils.toJson(Map.of("status", finalStatus.name(), "legs", legResponses)));
        repository.save(posting);
        log.info("Posting finalized | postingId={} status={} legs={}", posting.getPostingId(), finalStatus, legResponses.size());

        if (finalStatus == PostingStatus.ACSP && eventPublisher != null) {
            eventPublisher.publishSuccess(new PostingSuccessEvent(
                    posting.getPostingId(),
                    posting.getEndToEndReferenceId(),
                    posting.getRequestType(),
                    posting.getTargetSystems(),
                    Instant.now()
            ));
        }

        List<LegCreateResponseV2> legCreateResponses = legResponses.stream()
                .map(leg -> {
                    LegCreateResponseV2 r = new LegCreateResponseV2();
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

        AccountPostingCreateResponseV2 response = mapper.toCreateResponse(posting);
        response.setProcessedAt(Instant.now());
        response.setResponses(legCreateResponses);

        log.info("CREATE RESPONSE | {}", mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingResponseV2> search(AccountPostingSearchRequestV2 criteria, Pageable pageable) {
        log.info("SEARCH REQUEST | {}", mappingUtils.toJson(criteria));
        Page<AccountPostingResponseV2> result = repository
                .findAll(AccountPostingSpecification.from(criteria), pageable)
                .map(posting -> {
                    AccountPostingResponseV2 resp = mapper.toResponse(posting);
                    resp.setResponses(fetchLegs(posting.getPostingId()));
                    return resp;
                });
        log.info("SEARCH RESPONSE | totalElements={} totalPages={}", result.getTotalElements(), result.getTotalPages());
        return result;
    }

    /**
     * Checks the active table first. If not found, transparently falls back to the history
     * table so that callers never need to know which table holds the data.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountPostingResponseV2 findById(Long postingId) {
        log.info("FIND BY ID REQUEST | postingId={}", postingId);

        // 1. Check active table
        AccountPostingEntity posting = repository.findById(postingId).orElse(null);
        if (posting != null) {
            AccountPostingResponseV2 response = mapper.toResponse(posting);
            response.setResponses(fetchLegs(postingId));
            log.info("FIND BY ID RESPONSE (active) | {}", mappingUtils.toJson(response));
            return response;
        }

        // 2. Transparent fallback — posting has been archived
        log.info("FIND BY ID | postingId={} not in active table — checking history", postingId);
        AccountPostingHistoryEntity history = historyRepository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountPosting", postingId));
        AccountPostingResponseV2 response = mapper.toResponseFromHistory(history);
        response.setResponses(fetchHistoryLegs(postingId));
        log.info("FIND BY ID RESPONSE (history) | {}", mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingResponseV2> searchHistory(AccountPostingSearchRequestV2 criteria, Pageable pageable) {
        log.info("SEARCH HISTORY REQUEST | {}", mappingUtils.toJson(criteria));
        Page<AccountPostingResponseV2> result = historyRepository
                .findAll(AccountPostingHistorySpecification.from(criteria), pageable)
                .map(h -> {
                    AccountPostingResponseV2 resp = mapper.toResponseFromHistory(h);
                    resp.setResponses(fetchHistoryLegs(h.getPostingId()));
                    return resp;
                });
        log.info("SEARCH HISTORY RESPONSE | totalElements={} totalPages={}",
                result.getTotalElements(), result.getTotalPages());
        return result;
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
    public RetryResponseV2 retry(RetryRequestV2 request) {
        log.info("RETRY REQUEST | {}", mappingUtils.toJson(request));
        MDC.put("operation", "RETRY");
        try {
            RetryResponseV2 response = doRetry(request);
            log.info("RETRY RESPONSE | {}", mappingUtils.toJson(response));
            return response;
        } finally {
            MDC.remove("operation");
        }
    }

    private RetryResponseV2 doRetry(RetryRequestV2 request) {
        Instant now = Instant.now();
        Instant lockUntil = now.plusSeconds(LOCK_TTL_SECONDS);

        // 1. Determine candidate IDs
        List<Long> candidateIds = CollectionUtils.isEmpty(request.getPostingIds())
                ? repository.findEligibleForRetry(PostingStatus.PNDG, now)
                .stream().map(AccountPostingEntity::getPostingId).toList()
                : request.getPostingIds();

        log.info("RETRY START | candidates={} ids={}", candidateIds.size(), candidateIds);

        if (candidateIds.isEmpty()) {
            log.info("RETRY | No eligible postings — nothing to retry");
            return RetryResponseV2.builder().totalLegsRetried(0).successCount(0).failedCount(0)
                    .results(List.of()).build();
        }

        // 2. Lock + fetch in one short transaction that commits immediately.
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<AccountPostingEntity> lockedPostings = txTemplate.execute(tx -> {
            int lockedCount = repository.lockForRetry(candidateIds, PostingStatus.PNDG, now, lockUntil);
            log.info("RETRY | lockForRetry locked={} lockUntil={}", lockedCount, lockUntil);
            return repository.findByIdsAndLockUntil(candidateIds, lockUntil);
        });

        if (lockedPostings == null || lockedPostings.isEmpty()) {
            log.warn("RETRY | No postings locked — all already in progress or not PENDING");
            return RetryResponseV2.builder().totalLegsRetried(0).successCount(0).failedCount(0)
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
        List<CompletableFuture<List<RetryResponseV2.LegRetryResult>>> futures = lockedIds.stream()
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
        List<RetryResponseV2.LegRetryResult> allResults = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < futures.size(); i++) {
            Long postingId = lockedIds.get(i);
            try {
                List<RetryResponseV2.LegRetryResult> legResults = futures.get(i).get();
                log.info("RETRY | postingId={} legResults={} outcomes={}",
                        postingId, legResults.size(),
                        legResults.stream().map(r -> r.getPreviousStatus() + "→" + r.getNewStatus()).toList());
                allResults.addAll(legResults);
                for (RetryResponseV2.LegRetryResult r : legResults) {
                    if ("SUCCESS".equals(r.getNewStatus())) successCount++;
                    else failedCount++;
                }
            } catch (Exception ex) {
                log.error("RETRY | future failed | postingId={}", postingId, ex);
            }
        }

        log.info("RETRY DONE | totalLegs={} success={} failed={}", allResults.size(), successCount, failedCount);
        return RetryResponseV2.builder()
                .totalLegsRetried(allResults.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .results(allResults)
                .build();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<LegResponseV2> fetchLegs(Long postingId) {
        return legService.listLegs(postingId).stream()
                .map(mapper::toLegResponse)
                .toList();
    }

    private List<LegResponseV2> fetchHistoryLegs(Long postingId) {
        return legHistoryRepository.findByPostingIdOrderByLegOrder(postingId).stream()
                .map(legMapper::toResponseFromHistory)
                .map(mapper::toLegResponse)
                .toList();
    }

    // (bridge methods removed — strategies now accept V2 types directly)
}
