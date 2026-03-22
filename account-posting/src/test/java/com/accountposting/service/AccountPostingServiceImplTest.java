package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.PostingConfig;
import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.exception.BusinessException;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.mapper.AccountPostingMapper;
import com.accountposting.mapper.MappingUtils;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.accountposting.AccountPostingServiceImpl;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegService;
import com.accountposting.service.retry.PostingRetryProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountPostingServiceImplTest {

    @Mock
    AccountPostingRepository repository;
    @Mock
    AccountPostingMapper mapper;
    @Mock
    AccountPostingLegMapper legMapper;
    @Mock
    AccountPostingLegService legService;
    @Mock
    PostingConfigRepository postingConfigRepository;
    @Mock
    PostingStrategyFactory strategyFactory;
    @Mock
    PostingRetryProcessor retryProcessor;
    @Mock
    AccountPostingRequestValidator requestValidator;
    @Mock
    PostingEventPublisher eventPublisher;
    @Mock
    PlatformTransactionManager transactionManager;

    // Real ObjectMapper + MappingUtils — used for JSON serialization of request/response payloads
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final MappingUtils mappingUtils = new MappingUtils(objectMapper);

    private AccountPostingServiceImpl service;

    @BeforeEach
    void setUp() {
        // toCreateLegRequest copies targetSystem/operation/legOrder from its arguments
        // so that legService.addLeg() responses carry the correct values for strategy resolution.
        // lenient: retry tests don't call create(), so this stub is unused in those tests.
        lenient().when(legMapper.toCreateLegRequest(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    AccountPostingLegRequest r = new AccountPostingLegRequest();
                    r.setLegOrder(inv.getArgument(1));
                    r.setTargetSystem(inv.getArgument(2));
                    r.setOperation(inv.getArgument(4));
                    return r;
                });

        // TransactionTemplate calls transactionManager.getTransaction() → null (Mockito default, fine)
        // and commit() → no-op (void, Mockito default). The callback always executes inline.
        service = new AccountPostingServiceImpl(
                repository, mapper, legMapper, legService, postingConfigRepository,
                strategyFactory, retryProcessor, requestValidator, mappingUtils,
                Runnable::run, transactionManager
        );
    }

    // ── CREATE: all legs SUCCESS ───────────────────────────────────────────────

    @Test
    void create_allLegsSucceed_postingStatusIsSavedAsSuccess() {
        AccountPostingRequest request = buildRequest("e2e-001");
        AccountPostingEntity posting = buildPosting(1L, PostingStatus.PENDING);

        PostingConfig config1 = buildConfig(1, "CBS");
        PostingConfig config2 = buildConfig(2, "GL");
        PostingStrategy strategy1 = mock(PostingStrategy.class);
        PostingStrategy strategy2 = mock(PostingStrategy.class);

        when(repository.existsByEndToEndReferenceId("e2e-001")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of(config1, config2));
        when(legService.addLeg(anyLong(), any())).thenAnswer(inv -> {
            AccountPostingLegRequest r = inv.getArgument(1);
            AccountPostingLegResponse resp = new AccountPostingLegResponse();
            resp.setPostingLegId((long) (Math.random() * 1000 + 1));
            resp.setTargetSystem(r.getTargetSystem());
            resp.setOperation(r.getOperation());
            resp.setLegOrder(r.getLegOrder());
            return resp;
        });
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy1);
        when(strategyFactory.resolve("GL_POSTING")).thenReturn(strategy2);
        when(strategy1.process(eq(1L), eq(1), any(), eq(false), any()))
                .thenReturn(legResponse("SUCCESS"));
        when(strategy2.process(eq(1L), eq(2), any(), eq(false), any()))
                .thenReturn(legResponse("SUCCESS"));
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponse.builder().build());

        service.create(request);

        // Final save must persist SUCCESS status
        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.SUCCESS);

        // Both strategies must be called in order
        verify(strategy1).process(eq(1L), eq(1), any(), eq(false), any());
        verify(strategy2).process(eq(1L), eq(2), any(), eq(false), any());
    }

    // ── CREATE: no config found → FAILED + exception ──────────────────────────

    @Test
    void create_noConfigFound_savesFailedStatusAndThrowsBusinessException() {
        AccountPostingRequest request = buildRequest("e2e-002");
        AccountPostingEntity posting = buildPosting(2L, PostingStatus.PENDING);

        when(repository.existsByEndToEndReferenceId("e2e-002")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No posting config found");

        // FAILED status must be persisted before the exception is thrown
        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.FAILED);

        // No strategy should be called
        verifyNoInteractions(strategyFactory);
    }

    // ── CREATE: duplicate e2eRef ───────────────────────────────────────────────

    @Test
    void create_duplicateE2eRef_throwsBusinessExceptionWithoutPersisting() {
        AccountPostingRequest request = buildRequest("e2e-dup");
        when(repository.existsByEndToEndReferenceId("e2e-dup")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any());
        verifyNoInteractions(postingConfigRepository, strategyFactory);
    }

    // ── CREATE: one leg fails → PENDING (retryable) ────────────────────────────

    @Test
    void create_oneLegFails_postingStatusIsSavedAsPending() {
        AccountPostingRequest request = buildRequest("e2e-003");
        AccountPostingEntity posting = buildPosting(3L, PostingStatus.PENDING);

        PostingConfig config1 = buildConfig(1, "CBS");
        PostingConfig config2 = buildConfig(2, "GL");
        PostingStrategy strategy1 = mock(PostingStrategy.class);
        PostingStrategy strategy2 = mock(PostingStrategy.class);

        when(repository.existsByEndToEndReferenceId("e2e-003")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of(config1, config2));
        when(legService.addLeg(anyLong(), any())).thenAnswer(inv -> {
            AccountPostingLegRequest r = inv.getArgument(1);
            AccountPostingLegResponse resp = new AccountPostingLegResponse();
            resp.setPostingLegId((long) (Math.random() * 1000 + 1));
            resp.setTargetSystem(r.getTargetSystem());
            resp.setOperation(r.getOperation());
            resp.setLegOrder(r.getLegOrder());
            return resp;
        });
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy1);
        when(strategyFactory.resolve("GL_POSTING")).thenReturn(strategy2);
        when(strategy1.process(eq(3L), eq(1), any(), eq(false), any()))
                .thenReturn(legResponse("SUCCESS"));
        when(strategy2.process(eq(3L), eq(2), any(), eq(false), any()))
                .thenReturn(legResponse("FAILED"));
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponse.builder().build());

        service.create(request);

        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.PENDING);
    }

    // ── CREATE: all legs fail → PENDING ────────────────────────────────────────

    @Test
    void create_allLegsFail_postingStatusIsSavedAsPending() {
        AccountPostingRequest request = buildRequest("e2e-004");
        AccountPostingEntity posting = buildPosting(4L, PostingStatus.PENDING);

        PostingConfig config1 = buildConfig(1, "CBS");
        PostingStrategy strategy1 = mock(PostingStrategy.class);

        when(repository.existsByEndToEndReferenceId("e2e-004")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of(config1));
        when(legService.addLeg(anyLong(), any())).thenAnswer(inv -> {
            AccountPostingLegRequest r = inv.getArgument(1);
            AccountPostingLegResponse resp = new AccountPostingLegResponse();
            resp.setPostingLegId((long) (Math.random() * 1000 + 1));
            resp.setTargetSystem(r.getTargetSystem());
            resp.setOperation(r.getOperation());
            resp.setLegOrder(r.getLegOrder());
            return resp;
        });
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy1);
        when(strategy1.process(eq(4L), eq(1), any(), eq(false), any()))
                .thenReturn(legResponse("FAILED"));
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponse.builder().build());

        service.create(request);

        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.PENDING);
    }

    // ── RETRY: bulk — all PENDING postings ─────────────────────────────────────

    @Test
    void retry_bulkAllPending_locksAndProcessesEachPosting() {
        AccountPostingEntity p1 = buildPosting(10L, PostingStatus.PENDING);
        AccountPostingEntity p2 = buildPosting(11L, PostingStatus.PENDING);

        when(repository.findEligibleForRetry(eq(PostingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(p1, p2));
        when(repository.lockForRetry(eq(List.of(10L, 11L)), eq(PostingStatus.PENDING),
                any(Instant.class), any(Instant.class)))
                .thenReturn(2);
        when(repository.findByIdsAndLockUntil(eq(List.of(10L, 11L)), any(Instant.class)))
                .thenReturn(List.of(p1, p2));

        when(retryProcessor.process(10L)).thenReturn(List.of(
                legRetryResult(1L, 10L, "FAILED", "SUCCESS")));
        when(retryProcessor.process(11L)).thenReturn(List.of(
                legRetryResult(2L, 11L, "FAILED", "SUCCESS")));

        RetryRequest retryRequest = new RetryRequest(); // null postingIds → bulk

        RetryResponse response = service.retry(retryRequest);

        assertThat(response.getTotalLegsRetried()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailedCount()).isEqualTo(0);
        verify(retryProcessor).process(10L);
        verify(retryProcessor).process(11L);
    }

    // ── RETRY: specific posting IDs ────────────────────────────────────────────

    @Test
    void retry_specificPostingIds_onlyLocksAndProcessesThoseIds() {
        AccountPostingEntity p1 = buildPosting(20L, PostingStatus.PENDING);

        when(repository.lockForRetry(eq(List.of(20L)), eq(PostingStatus.PENDING),
                any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(repository.findByIdsAndLockUntil(eq(List.of(20L)), any(Instant.class)))
                .thenReturn(List.of(p1));
        when(retryProcessor.process(20L)).thenReturn(List.of(
                legRetryResult(5L, 20L, "FAILED", "FAILED")));

        RetryRequest retryRequest = new RetryRequest();
        retryRequest.setPostingIds(List.of(20L));

        RetryResponse response = service.retry(retryRequest);

        assertThat(response.getTotalLegsRetried()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getFailedCount()).isEqualTo(1);
        verify(repository, never()).findEligibleForRetry(any(), any());
        verify(retryProcessor).process(20L);
    }

    // ── RETRY: nothing eligible ────────────────────────────────────────────────

    @Test
    void retry_noEligiblePostings_returnsEmptyResponse() {
        when(repository.findEligibleForRetry(eq(PostingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());

        RetryResponse response = service.retry(new RetryRequest());

        assertThat(response.getTotalLegsRetried()).isEqualTo(0);
        verifyNoInteractions(retryProcessor);
    }

    // ── RETRY: all postings already locked ─────────────────────────────────────

    @Test
    void retry_allPostingsAlreadyLocked_returnsEmptyResponse() {
        AccountPostingEntity p1 = buildPosting(30L, PostingStatus.PENDING);

        when(repository.findEligibleForRetry(eq(PostingStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(p1));
        when(repository.lockForRetry(anyList(), any(), any(), any())).thenReturn(0);
        when(repository.findByIdsAndLockUntil(anyList(), any())).thenReturn(List.of());

        RetryResponse response = service.retry(new RetryRequest());

        assertThat(response.getTotalLegsRetried()).isEqualTo(0);
        verifyNoInteractions(retryProcessor);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AccountPostingRequest buildRequest(String e2eRef) {
        AccountPostingRequest req = new AccountPostingRequest();
        req.setSourceReferenceId("SRC-001");
        req.setEndToEndReferenceId(e2eRef);
        req.setSourceName("IMX");
        req.setRequestType("IMX_CBS_GL");
        req.setAmount(new BigDecimal("1000.00"));
        req.setCurrency("USD");
        req.setCreditDebitIndicator(CreditDebitIndicator.CREDIT);
        req.setDebtorAccount("ACC-DEBIT");
        req.setCreditorAccount("ACC-CREDIT");
        req.setRequestedExecutionDate(LocalDate.now());
        return req;
    }

    private AccountPostingEntity buildPosting(Long id, PostingStatus status) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(id);
        p.setStatus(status);
        p.setRequestType("FT");
        p.setEndToEndReferenceId("e2e-" + id);
        return p;
    }

    private PostingConfig buildConfig(int orderSeq, String targetSystem) {
        PostingConfig c = new PostingConfig();
        c.setOrderSeq(orderSeq);
        c.setTargetSystem(targetSystem);
        c.setOperation("POSTING");
        c.setRequestType("FT");
        return c;
    }

    private LegResponse legResponse(String status) {
        LegResponse r = new LegResponse();
        r.setStatus(status);
        return r;
    }

    private RetryResponse.LegRetryResult legRetryResult(Long legId, Long postingId,
                                                        String prev, String next) {
        return RetryResponse.LegRetryResult.builder()
                .postingLegId(legId)
                .postingId(postingId)
                .previousStatus(prev)
                .newStatus(next)
                .build();
    }
}
