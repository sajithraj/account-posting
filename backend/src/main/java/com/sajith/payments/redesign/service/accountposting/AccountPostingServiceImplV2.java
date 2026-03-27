package com.sajith.payments.redesign.service.accountposting;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingSearchRequestV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegCreateResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.AccountPostingHistoryEntity;
import com.sajith.payments.redesign.entity.PostingConfig;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.exception.BusinessException;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.mapper.AccountPostingLegMapperV2;
import com.sajith.payments.redesign.mapper.AccountPostingMapperV2;
import com.sajith.payments.redesign.repository.AccountPostingHistoryRepository;
import com.sajith.payments.redesign.repository.AccountPostingHistorySpecification;
import com.sajith.payments.redesign.repository.AccountPostingLegHistoryRepository;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.repository.AccountPostingSpecification;
import com.sajith.payments.redesign.repository.PostingConfigRepository;
import com.sajith.payments.redesign.service.AccountPostingRequestValidatorV2;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategyFactory;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
import com.sajith.payments.redesign.service.retry.PostingRetryProcessorV2;
import com.sajith.payments.redesign.utils.AppUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private static final int LOCK_TTL_SECONDS = 120;
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
    private final AppUtility appUtility;
    @Qualifier("retryExecutor")
    private final Executor retryExecutor;

    // noRollbackFor: pre-leg failures (e.g. no config) persist the FAILED status before throwing
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AccountPostingCreateResponseV2 create(IncomingPostingRequest request) {
        MDC.put("e2eRef", request.getEndToEndRefId());
        MDC.put("requestType", request.getRequestType());
        try {
            return doCreate(request);
        } finally {
            MDC.remove("e2eRef");
            MDC.remove("requestType");
            MDC.remove("postingId");
            MDC.remove("PostingLegId");
        }
    }

    private AccountPostingCreateResponseV2 doCreate(IncomingPostingRequest request) {
        log.info("Request received for create posting :: {} . ", appUtility.toObjectToString(request));

        if (repository.existsByEndToEndReferenceId(request.getEndToEndRefId())) {
            log.error("Duplicate posting received so rejected for end to end reference id :: {}", request.getEndToEndRefId());
            throw new BusinessException("DUPLICATE_E2E_REF",
                    "Posting already exists for endToEndReferenceId: " + request.getEndToEndRefId());
        }

        // Save an initial PENDING record first so validation failures are always auditable in the DB.
        AccountPostingEntity posting = mapper.toEntity(request);
        posting.setRequestPayload(appUtility.toObjectToString(request));
        posting = repository.save(posting);
        MDC.put("postingId", String.valueOf(posting.getPostingId()));
        log.info("Initial posting requested persisted. PostingId :: {} initial posting status :: {} .", posting.getPostingId(), posting.getStatus());

        try {
            requestValidator.validate(request);
        } catch (BusinessException ex) {
            posting.setStatus(PostingStatus.RJCT);
            posting.setReason(ex.getMessage());
            posting.setResponsePayload(appUtility.toObjectToString(Map.of("error", ex.getMessage())));
            repository.save(posting);
            log.error("Posting marked RJCT due to validation. Posting id :: {} reason :: {} .", posting.getPostingId(), ex.getMessage());
            throw ex;
        }

        // Load routing config ordered by execution sequence.
        List<PostingConfig> configs = postingConfigRepository
                .findByRequestTypeOrderByOrderSeqAsc(request.getRequestType())
                .stream()
                .sorted(Comparator.comparingInt(PostingConfig::getOrderSeq))
                .toList();
        log.info("Fetched config details for request type :: {}. Config fetched :: {} .", request.getRequestType(), appUtility.toObjectToString(configs));

        if (configs.isEmpty()) {
            String reason = "No posting config found for requestType: " + request.getRequestType();
            posting.setStatus(PostingStatus.RJCT);
            posting.setReason(reason);
            posting.setResponsePayload(appUtility.toObjectToString(Map.of("error", reason)));
            repository.save(posting);
            log.error("Posting marked RJCT - no config found for request type :: {} . ", request.getRequestType());
            throw new BusinessException("NO_CONFIG_FOUND", reason);
        }

        String targetSystems = configs.stream()
                .map(PostingConfig::getTargetSystem)
                .collect(Collectors.joining("_"));
        posting.setTargetSystems(targetSystems);
        repository.save(posting);
        log.info("Target systems updated for posting id :: {} as target systems :: {} .", posting.getPostingId(), targetSystems);

        // Pre-insert all legs as PENDING before calling any external system. This guarantees every leg is visible and retryable even if an earlier call fails.
        List<AccountPostingLegResponseV2> preInsertedLegs = new ArrayList<>();
        for (PostingConfig config : configs) {
            AccountPostingLegRequestV2 legReq = legMapper.toCreateLegRequest(request, config.getOrderSeq(), config.getTargetSystem(), LegMode.NORM, config.getOperation(), null);
            AccountPostingLegResponseV2 pre = legService.addLeg(posting.getPostingId(), legReq);
            preInsertedLegs.add(pre);
            log.info("Legs has been persisted before processing for target system :: {} operation :: {} order to execute :: {}. Generated posting leg id  :: {} .", pre.getTargetSystem(), pre.getOperation(), pre.getLegOrder(), pre.getPostingLegId());
        }

        List<LegResponseV2> legResponses = new ArrayList<>();
        boolean allSuccess = true;

        for (AccountPostingLegResponseV2 pre : preInsertedLegs) {
            MDC.put("PostingLegId", String.valueOf(pre.getPostingLegId()));
            try {
                log.info("Fetching leg details to process for target system :: {} operation :: {} .", pre.getTargetSystem(), pre.getOperation());
                PostingStrategy strategy = strategyFactory.resolve(pre.getTargetSystem() + "_" + pre.getOperation());
                LegResponseV2 legResponse = strategy.process(posting.getPostingId(), pre.getLegOrder(), request, false, pre.getPostingLegId());
                legResponses.add(legResponse);
                log.info("Leg processing completed for target system :: {} operation :: {} amd leg status {} .", pre.getTargetSystem(), pre.getOperation(), legResponse.getStatus());
                if (!"SUCCESS".equalsIgnoreCase(legResponse.getStatus())) {
                    allSuccess = false;
                }
            } catch (Exception ex) {
                log.error("Failed while processing leg execution for target system :: {} operation :: {}. Error message :: {} .", pre.getTargetSystem(), pre.getOperation(), ex.getMessage());
                allSuccess = false;
            }
            MDC.remove("PostingLegId");
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

        List<LegCreateResponseV2> legCreateResponses = legMapper.toLegCreateResponses(legResponses);

        AccountPostingCreateResponseV2 response = mapper.toCreateResponse(posting);
        response.setProcessedAt(Instant.now());
        response.setResponses(legCreateResponses);

        posting.setResponsePayload(appUtility.toObjectToString(response));
        repository.save(posting);
        log.info("Create posting completed and posting status :: {}. Response to send to target system {} .", finalStatus, appUtility.toObjectToString(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingFullResponseV2> search(AccountPostingSearchRequestV2 criteria, Pageable pageable) {
        log.info("Request received for posting search based on the criteria. Request received :: {} .", appUtility.toObjectToString(criteria));
        Page<AccountPostingFullResponseV2> result = repository
                .findAll(AccountPostingSpecification.from(criteria), pageable)
                .map(posting -> {
                    AccountPostingFullResponseV2 resp = mapper.toResponse(posting);
                    resp.setResponses(fetchLegs(posting.getPostingId()));
                    return resp;
                });
        log.info("Response to send based on search criteria received. Total elements :: {} totalPages :: {} .", result.getTotalElements(), result.getTotalPages());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingFullResponseV2 findById(Long postingId) {
        log.info("Request received to find the posting by posting id :: {} .", postingId);
        // Check posting table if not check in hist table
        AccountPostingEntity posting = repository.findById(postingId).orElse(null);
        if (posting != null) {
            AccountPostingFullResponseV2 response = mapper.toResponse(posting);
            response.setResponses(fetchLegs(postingId));
            log.info("Response to send for find the posting by posting id :: {} response {} .", posting, appUtility.toObjectToString(response));
            return response;
        }
        // 2. Transparent fallback - posting has been archived
        log.info("Posting id not found in the posting table so going to check in posting history table {} .", postingId);
        AccountPostingHistoryEntity history = historyRepository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("AccountPosting", postingId));
        AccountPostingFullResponseV2 response = mapper.toResponseFromHistory(history);
        response.setResponses(fetchHistoryLegs(postingId));
        log.info("Response to send for find the posting by posting id :: {} response {} .", posting, appUtility.toObjectToString(response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountPostingFullResponseV2> searchHistory(AccountPostingSearchRequestV2 criteria, Pageable pageable) {
        log.info("Request received for posting search from history table based on the criteria. Request received :: {} .", appUtility.toObjectToString(criteria));
        log.info("SEARCH HISTORY REQUEST | {}", appUtility.toObjectToString(criteria));
        Page<AccountPostingFullResponseV2> result = historyRepository
                .findAll(AccountPostingHistorySpecification.from(criteria), pageable)
                .map(h -> {
                    AccountPostingFullResponseV2 resp = mapper.toResponseFromHistory(h);
                    resp.setResponses(fetchHistoryLegs(h.getPostingId()));
                    return resp;
                });
        log.info("Response to send based on search criteria received for history table. Total elements :: {} totalPages :: {} .", result.getTotalElements(), result.getTotalPages());
        return result;
    }

    @Override
    public RetryResponseV2 retry(RetryRequestV2 request) {
        log.info("Request received for retry posting :: {} . ", appUtility.toObjectToString(request));
        MDC.put("operation", "RETRY");
        try {
            RetryResponseV2 response = doRetry(request);
            log.info("Response to send to target system {} .", appUtility.toObjectToString(response));
            return response;
        } finally {
            MDC.remove("operation");
            MDC.remove("e2eRef");
            MDC.remove("requestType");
            MDC.remove("postingId");
            MDC.remove("PostingLegId");
        }
    }

    private RetryResponseV2 doRetry(RetryRequestV2 request) {
        Instant now = Instant.now();
        Instant lockUntil = now.plusSeconds(LOCK_TTL_SECONDS);
        boolean specificIds = !CollectionUtils.isEmpty(request.getPostingIds());

        // 1. Resolve candidate IDs, then lock them atomically
        List<Long> lockedIds = specificIds
                ? request.getPostingIds()
                : repository.findEligibleIdsForRetry(PostingStatus.PNDG, now);
        if (lockedIds.isEmpty()) {
            log.warn("No postings locked - all already in progress or not PENDING");
            return RetryResponseV2.builder().totalPostings(0).successCount(0).failedCount(0).build();
        }

        int locked = repository.lockEligibleByIds(lockedIds, PostingStatus.PNDG, now, lockUntil);
        log.info("Eligible posting id's for the retry :: {} and {} posting id's has been locked({}) for retry .", lockedIds, lockedIds.size(), locked);

        // 2. Capture MDC so each async thread inherits the same traceId
        final Map<String, String> parentMdc = java.util.Optional
                .ofNullable(MDC.getCopyOfContextMap()).orElse(Map.of());

        // 3. Dispatch one future per posting - each owns its own TX
        List<CompletableFuture<Boolean>> futures = lockedIds.stream()
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
        log.info("All futures completed - aggregating");

        // 4. Aggregate at posting level
        int successCount = 0;
        int failedCount = 0;

        for (int i = 0; i < futures.size(); i++) {
            Long postingId = lockedIds.get(i);
            try {
                boolean succeeded = futures.get(i).get();
                log.info("Retry result | postingId={} succeeded={}", postingId, succeeded);
                if (succeeded) successCount++;
                else failedCount++;
            } catch (Exception ex) {
                log.error("Retry future failed | postingId={}", postingId, ex);
                failedCount++;
            }
        }

        log.info("Retry completed | totalPostings={} success={} failed={}", lockedIds.size(), successCount, failedCount);
        return RetryResponseV2.builder()
                .totalPostings(lockedIds.size())
                .successCount(successCount)
                .failedCount(failedCount)
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

}
