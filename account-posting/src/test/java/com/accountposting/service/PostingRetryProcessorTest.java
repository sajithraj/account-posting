package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.event.PostingEventPublisher;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.service.accountposting.strategy.PostingStrategy;
import com.accountposting.service.accountposting.strategy.PostingStrategyFactory;
import com.accountposting.service.accountpostingleg.AccountPostingLegService;
import com.accountposting.service.retry.PostingRetryProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingRetryProcessorTest {

    @Mock
    AccountPostingRepository postingRepository;
    @Mock
    AccountPostingLegService legService;
    @Mock
    PostingStrategyFactory strategyFactory;
    @Mock
    PostingEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PostingRetryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PostingRetryProcessor(postingRepository, legService, strategyFactory, objectMapper);
    }

    // ── posting not found ──────────────────────────────────────────────────────

    @Test
    void process_postingNotFound_returnsEmptyList() {
        when(postingRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        List<RetryResponse.LegRetryResult> results = processor.process(99L);

        assertThat(results).isEmpty();
        verifyNoInteractions(legService, strategyFactory);
    }

    // ── no non-success legs ────────────────────────────────────────────────────

    @Test
    void process_noNonSuccessLegs_returnsEmptyList() {
        AccountPostingEntity posting = buildPosting(1L, "FT");
        when(postingRepository.findById(1L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(1L)).thenReturn(List.of());

        List<RetryResponse.LegRetryResult> results = processor.process(1L);

        assertThat(results).isEmpty();
        verifyNoInteractions(strategyFactory);
    }

    // ── one leg retried successfully ───────────────────────────────────────────

    @Test
    void process_onePendingLeg_retriesAndUpdatesPostingToSuccess() {
        AccountPostingEntity posting = buildPosting(1L, "FT");
        AccountPostingLegResponse leg = buildLegResponse(10L, 1, "CBS", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        LegResponse legResult = buildLegResponse("SUCCESS", 10L);

        when(postingRepository.findById(1L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(1L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy);
        when(strategy.process(eq(1L), eq(1), any(), eq(true), eq(10L))).thenReturn(legResult);
        when(legService.listLegs(1L)).thenReturn(List.of(buildLegResponseSuccess(10L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RetryResponse.LegRetryResult> results = processor.process(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPreviousStatus()).isEqualTo("FAILED");
        assertThat(results.get(0).getNewStatus()).isEqualTo("SUCCESS");

        // Posting status should be updated to SUCCESS
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.ACSP));
    }

    // ── multiple legs — sequential by order ───────────────────────────────────

    @Test
    void process_multipleFailedLegs_retriesSequentiallyByOrder() {
        AccountPostingEntity posting = buildPosting(2L, "FT");
        AccountPostingLegResponse leg1 = buildLegResponse(11L, 1, "CBS", LegStatus.FAILED);
        AccountPostingLegResponse leg2 = buildLegResponse(12L, 2, "GL", LegStatus.PENDING);

        PostingStrategy cbsStrategy = mock(PostingStrategy.class);
        PostingStrategy glStrategy = mock(PostingStrategy.class);

        when(postingRepository.findById(2L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(2L)).thenReturn(List.of(leg1, leg2));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(cbsStrategy);
        when(strategyFactory.resolve("GL_POSTING")).thenReturn(glStrategy);
        when(cbsStrategy.process(eq(2L), eq(1), any(), eq(true), eq(11L)))
                .thenReturn(buildLegResponse("SUCCESS", 11L));
        when(glStrategy.process(eq(2L), eq(2), any(), eq(true), eq(12L)))
                .thenReturn(buildLegResponse("SUCCESS", 12L));
        when(legService.listLegs(2L)).thenReturn(
                List.of(buildLegResponseSuccess(11L), buildLegResponseSuccess(12L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RetryResponse.LegRetryResult> results = processor.process(2L);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(RetryResponse.LegRetryResult::getNewStatus)
                .containsExactly("SUCCESS", "SUCCESS");

        // Verify execution order: CBS before GL
        var inOrder = inOrder(cbsStrategy, glStrategy);
        inOrder.verify(cbsStrategy).process(eq(2L), eq(1), any(), eq(true), eq(11L));
        inOrder.verify(glStrategy).process(eq(2L), eq(2), any(), eq(true), eq(12L));

        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.ACSP));
    }

    // ── one leg still fails after retry → posting stays PENDING ───────────────

    @Test
    void process_legStillFailsAfterRetry_postingRemainsAsPending() {
        AccountPostingEntity posting = buildPosting(3L, "FT");
        AccountPostingLegResponse leg = buildLegResponse(20L, 1, "OBPM", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        when(postingRepository.findById(3L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(3L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("OBPM_POSTING")).thenReturn(strategy);
        when(strategy.process(eq(3L), eq(1), any(), eq(true), eq(20L)))
                .thenReturn(buildLegResponse("FAILED", 20L));
        // One leg still FAILED in full list
        when(legService.listLegs(3L)).thenReturn(List.of(buildLegResponseFailed(20L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RetryResponse.LegRetryResult> results = processor.process(3L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNewStatus()).isEqualTo("FAILED");
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.PNDG));
    }

    // ── strategy throws exception → leg recorded as FAILED ────────────────────

    @Test
    void process_strategyThrows_legRecordedAsFailedAndContinues() {
        AccountPostingEntity posting = buildPosting(4L, "FT");
        AccountPostingLegResponse leg = buildLegResponse(30L, 1, "CBS", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        when(postingRepository.findById(4L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(4L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy);
        when(strategy.process(any(), anyInt(), any(), eq(true), eq(30L)))
                .thenThrow(new RuntimeException("CBS timeout"));
        when(legService.listLegs(4L)).thenReturn(List.of(buildLegResponseFailed(30L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RetryResponse.LegRetryResult> results = processor.process(4L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNewStatus()).isEqualTo("FAILED");
        assertThat(results.get(0).getReason()).contains("CBS timeout");
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.PNDG));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AccountPostingEntity buildPosting(Long id, String requestType) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(id);
        p.setStatus(PostingStatus.PNDG);
        p.setRequestType(requestType);
        try {
            AccountPostingRequest req = new AccountPostingRequest();
            req.setSourceReferenceId("SRC");
            req.setEndToEndReferenceId("e2e-" + id);
            req.setSourceName("IMX");
            req.setRequestType("IMX_CBS_GL");
            req.setAmount(new BigDecimal("500.00"));
            req.setCurrency("USD");
            req.setCreditDebitIndicator(CreditDebitIndicator.DEBIT);
            req.setDebtorAccount("ACC-D");
            req.setCreditorAccount("ACC-C");
            req.setRequestedExecutionDate(LocalDate.now());
            p.setRequestPayload(objectMapper.writeValueAsString(req));
        } catch (Exception ignored) {
        }
        return p;
    }

    private AccountPostingLegResponse buildLegResponse(Long legId, int order,
                                                       String targetSystem, LegStatus status) {
        AccountPostingLegResponse r = new AccountPostingLegResponse();
        r.setPostingLegId(legId);
        r.setLegOrder(order);
        r.setTargetSystem(targetSystem);
        r.setOperation("POSTING");
        r.setStatus(status);
        r.setAttemptNumber(1);
        return r;
    }

    private AccountPostingLegResponse buildLegResponseSuccess(Long legId) {
        AccountPostingLegResponse r = new AccountPostingLegResponse();
        r.setPostingLegId(legId);
        r.setStatus(LegStatus.SUCCESS);
        r.setAttemptNumber(2);
        return r;
    }

    private AccountPostingLegResponse buildLegResponseFailed(Long legId) {
        AccountPostingLegResponse r = new AccountPostingLegResponse();
        r.setPostingLegId(legId);
        r.setStatus(LegStatus.FAILED);
        r.setAttemptNumber(2);
        return r;
    }

    private LegResponse buildLegResponse(String status, Long legId) {
        LegResponse r = new LegResponse();
        r.setStatus(status);
        return r;
    }
}
