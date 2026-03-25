package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.dto.retry.RetryRequestV2;
import com.accountposting.dto.retry.RetryResponseV2;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.PostingConfig;
import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.exception.BusinessException;
import com.accountposting.mapper.AccountPostingLegMapperV2;
import com.accountposting.mapper.AccountPostingMapperV2;
import com.accountposting.repository.AccountPostingHistoryRepository;
import com.accountposting.repository.AccountPostingLegHistoryRepository;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.accountposting.AccountPostingServiceImplV2;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import com.accountposting.service.retry.PostingRetryProcessorV2;
import com.accountposting.utils.AppUtility;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    AccountPostingHistoryRepository historyRepository;
    @Mock
    AccountPostingLegHistoryRepository legHistoryRepository;
    @Mock
    AccountPostingMapperV2 mapper;
    @Mock
    AccountPostingLegMapperV2 legMapper;
    @Mock
    AccountPostingLegServiceV2 legService;
    @Mock
    PostingConfigRepository postingConfigRepository;
    @Mock
    PostingStrategyFactory strategyFactory;
    @Mock
    PostingRetryProcessorV2 retryProcessor;
    @Mock
    AccountPostingRequestValidatorV2 requestValidator;
    @Mock
    PostingEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AppUtility appUtility = new AppUtility(objectMapper);

    private AccountPostingServiceImplV2 service;

    @BeforeEach
    void setUp() {
        // toCreateLegRequest copies targetSystem/operation/legOrder from its arguments
        // so that legService.addLeg() responses carry the correct values for strategy resolution.
        lenient().when(legMapper.toCreateLegRequest(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    AccountPostingLegRequestV2 r = new AccountPostingLegRequestV2();
                    r.setLegOrder(inv.getArgument(1));
                    r.setTargetSystem(inv.getArgument(2));
                    r.setOperation(inv.getArgument(4));
                    return r;
                });

        // TransactionTemplate calls transactionManager.getTransaction() → null (Mockito default, fine)
        // and commit() → no-op (void, Mockito default). The callback always executes inline.
        service = new AccountPostingServiceImplV2(
                repository, historyRepository, legHistoryRepository,
                mapper, legMapper, legService, postingConfigRepository,
                strategyFactory, retryProcessor, requestValidator, appUtility,
                Runnable::run
        );
    }

    // ── CREATE: all legs SUCCESS ───────────────────────────────────────────────

    @Test
    void create_allLegsSucceed_postingStatusIsSavedAsSuccess() {
        AccountPostingRequestV2 request = buildRequest("e2e-001");
        AccountPostingEntity posting = buildPosting(1L, PostingStatus.PNDG);

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
            AccountPostingLegRequestV2 r = inv.getArgument(1);
            AccountPostingLegResponseV2 resp = new AccountPostingLegResponseV2();
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
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponseV2.builder().build());

        service.create(request);

        // Final save must persist SUCCESS status
        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.ACSP);

        // Both strategies must be called in order
        verify(strategy1).process(eq(1L), eq(1), any(), eq(false), any());
        verify(strategy2).process(eq(1L), eq(2), any(), eq(false), any());
    }

    // ── CREATE: no config found → FAILED + exception ──────────────────────────

    @Test
    void create_noConfigFound_savesFailedStatusAndThrowsBusinessException() {
        AccountPostingRequestV2 request = buildRequest("e2e-002");
        AccountPostingEntity posting = buildPosting(2L, PostingStatus.PNDG);

        when(repository.existsByEndToEndReferenceId("e2e-002")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No posting config found");

        // RJCT status must be persisted before the exception is thrown
        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.RJCT);

        // No strategy should be called
        verifyNoInteractions(strategyFactory);
    }

    // ── CREATE: duplicate e2eRef ───────────────────────────────────────────────

    @Test
    void create_duplicateE2eRef_throwsBusinessExceptionWithoutPersisting() {
        AccountPostingRequestV2 request = buildRequest("e2e-dup");
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
        AccountPostingRequestV2 request = buildRequest("e2e-003");
        AccountPostingEntity posting = buildPosting(3L, PostingStatus.PNDG);

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
            AccountPostingLegRequestV2 r = inv.getArgument(1);
            AccountPostingLegResponseV2 resp = new AccountPostingLegResponseV2();
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
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponseV2.builder().build());

        service.create(request);

        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.PNDG);
    }

    // ── CREATE: all legs fail → PENDING ────────────────────────────────────────

    @Test
    void create_allLegsFail_postingStatusIsSavedAsPending() {
        AccountPostingRequestV2 request = buildRequest("e2e-004");
        AccountPostingEntity posting = buildPosting(4L, PostingStatus.PNDG);

        PostingConfig config1 = buildConfig(1, "CBS");
        PostingStrategy strategy1 = mock(PostingStrategy.class);

        when(repository.existsByEndToEndReferenceId("e2e-004")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(posting);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postingConfigRepository.findByRequestTypeOrderByOrderSeqAsc("IMX_CBS_GL"))
                .thenReturn(List.of(config1));
        when(legService.addLeg(anyLong(), any())).thenAnswer(inv -> {
            AccountPostingLegRequestV2 r = inv.getArgument(1);
            AccountPostingLegResponseV2 resp = new AccountPostingLegResponseV2();
            resp.setPostingLegId((long) (Math.random() * 1000 + 1));
            resp.setTargetSystem(r.getTargetSystem());
            resp.setOperation(r.getOperation());
            resp.setLegOrder(r.getLegOrder());
            return resp;
        });
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy1);
        when(strategy1.process(eq(4L), eq(1), any(), eq(false), any()))
                .thenReturn(legResponse("FAILED"));
        when(mapper.toCreateResponse(any(AccountPostingEntity.class))).thenReturn(AccountPostingCreateResponseV2.builder().build());

        service.create(request);

        ArgumentCaptor<AccountPostingEntity> captor = ArgumentCaptor.forClass(AccountPostingEntity.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountPostingEntity::getStatus)
                .contains(PostingStatus.PNDG);
    }

    // ── RETRY: bulk — all PENDING postings ─────────────────────────────────────

    @Test
    void retry_bulkAllPending_locksAndProcessesEachPosting() {
        when(repository.findEligibleIdsForRetry(eq(PostingStatus.PNDG), any(Instant.class)))
                .thenReturn(List.of(10L, 11L));
        when(repository.lockEligibleByIds(eq(List.of(10L, 11L)), eq(PostingStatus.PNDG),
                any(Instant.class), any(Instant.class)))
                .thenReturn(2);

        when(retryProcessor.process(10L)).thenReturn(true);
        when(retryProcessor.process(11L)).thenReturn(true);

        RetryRequestV2 retryRequest = new RetryRequestV2(); // null postingIds -> bulk

        RetryResponseV2 response = service.retry(retryRequest);

        assertThat(response.getTotalPostings()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        assertThat(response.getFailedCount()).isEqualTo(0);
        verify(retryProcessor).process(10L);
        verify(retryProcessor).process(11L);
    }

    // ── RETRY: specific posting IDs ────────────────────────────────────────────

    @Test
    void retry_specificPostingIds_onlyLocksAndProcessesThoseIds() {
        when(repository.lockEligibleByIds(eq(List.of(20L)), eq(PostingStatus.PNDG),
                any(Instant.class), any(Instant.class)))
                .thenReturn(1);
        when(retryProcessor.process(20L)).thenReturn(false);

        RetryRequestV2 retryRequest = new RetryRequestV2();
        retryRequest.setPostingIds(List.of(20L));

        RetryResponseV2 response = service.retry(retryRequest);

        assertThat(response.getTotalPostings()).isEqualTo(1);
        assertThat(response.getSuccessCount()).isEqualTo(0);
        assertThat(response.getFailedCount()).isEqualTo(1);
        verify(repository, never()).findEligibleIdsForRetry(any(), any());
        verify(retryProcessor).process(20L);
    }

    // ── RETRY: nothing eligible ────────────────────────────────────────────────

    @Test
    void retry_noEligiblePostings_returnsEmptyResponse() {
        when(repository.findEligibleIdsForRetry(eq(PostingStatus.PNDG), any(Instant.class)))
                .thenReturn(List.of());

        RetryResponseV2 response = service.retry(new RetryRequestV2());

        assertThat(response.getTotalPostings()).isEqualTo(0);
        verifyNoInteractions(retryProcessor);
    }

    // ── RETRY: all postings already locked ─────────────────────────────────────

    @Test
    void retry_allPostingsAlreadyLocked_returnsEmptyResponse() {
        when(repository.findEligibleIdsForRetry(any(), any())).thenReturn(List.of());

        RetryResponseV2 response = service.retry(new RetryRequestV2());

        assertThat(response.getTotalPostings()).isEqualTo(0);
        verifyNoInteractions(retryProcessor);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AccountPostingRequestV2 buildRequest(String e2eRef) {
        AccountPostingRequestV2 req = new AccountPostingRequestV2();
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

    private LegResponseV2 legResponse(String status) {
        LegResponseV2 r = new LegResponseV2();
        r.setStatus(status);
        return r;
    }

}
