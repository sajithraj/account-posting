package com.sajith.payments.redesign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajith.payments.redesign.dto.accountposting.Amount;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategy;
import com.sajith.payments.redesign.service.accountposting.strategy.PostingStrategyFactory;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
import com.sajith.payments.redesign.service.retry.PostingRetryProcessorV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    AccountPostingLegServiceV2 legService;
    @Mock
    PostingStrategyFactory strategyFactory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PostingRetryProcessorV2 processor;

    @BeforeEach
    void setUp() {
        processor = new PostingRetryProcessorV2(postingRepository, legService, strategyFactory, objectMapper);
    }

    // ── posting not found ──────────────────────────────────────────────────────

    @Test
    void process_postingNotFound_returnsFalse() {
        when(postingRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        boolean result = processor.process(99L);

        assertThat(result).isFalse();
        verifyNoInteractions(legService, strategyFactory);
    }

    // ── no non-success legs ────────────────────────────────────────────────────

    @Test
    void process_noNonSuccessLegs_returnsFalse() {
        AccountPostingEntity posting = buildPosting(1L, "FT");
        when(postingRepository.findById(1L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(1L)).thenReturn(List.of());

        boolean result = processor.process(1L);

        assertThat(result).isFalse();
        verifyNoInteractions(strategyFactory);
    }

    // ── one leg retried successfully ───────────────────────────────────────────

    @Test
    void process_onePendingLeg_retriesAndUpdatesPostingToSuccess() {
        AccountPostingEntity posting = buildPosting(1L, "FT");
        AccountPostingLegResponseV2 leg = buildLegResponse(10L, 1, "CBS", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        LegResponseV2 legResult = buildLegResponseV2("SUCCESS", 10L);

        when(postingRepository.findById(1L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(1L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy);
        when(strategy.process(eq(1L), eq(1), any(), eq(true), eq(10L))).thenReturn(legResult);
        when(legService.listLegs(1L)).thenReturn(List.of(buildLegResponseSuccess(10L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.process(1L);

        assertThat(result).isTrue();
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.ACSP));
    }

    // ── multiple legs — sequential by order ───────────────────────────────────

    @Test
    void process_multipleFailedLegs_retriesSequentiallyByOrder() {
        AccountPostingEntity posting = buildPosting(2L, "FT");
        AccountPostingLegResponseV2 leg1 = buildLegResponse(11L, 1, "CBS", LegStatus.FAILED);
        AccountPostingLegResponseV2 leg2 = buildLegResponse(12L, 2, "GL", LegStatus.PENDING);

        PostingStrategy cbsStrategy = mock(PostingStrategy.class);
        PostingStrategy glStrategy = mock(PostingStrategy.class);

        when(postingRepository.findById(2L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(2L)).thenReturn(List.of(leg1, leg2));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(cbsStrategy);
        when(strategyFactory.resolve("GL_POSTING")).thenReturn(glStrategy);
        when(cbsStrategy.process(eq(2L), eq(1), any(), eq(true), eq(11L)))
                .thenReturn(buildLegResponseV2("SUCCESS", 11L));
        when(glStrategy.process(eq(2L), eq(2), any(), eq(true), eq(12L)))
                .thenReturn(buildLegResponseV2("SUCCESS", 12L));
        when(legService.listLegs(2L)).thenReturn(
                List.of(buildLegResponseSuccess(11L), buildLegResponseSuccess(12L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.process(2L);

        assertThat(result).isTrue();

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
        AccountPostingLegResponseV2 leg = buildLegResponse(20L, 1, "OBPM", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        when(postingRepository.findById(3L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(3L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("OBPM_POSTING")).thenReturn(strategy);
        when(strategy.process(eq(3L), eq(1), any(), eq(true), eq(20L)))
                .thenReturn(buildLegResponseV2("FAILED", 20L));
        // One leg still FAILED in full list
        when(legService.listLegs(3L)).thenReturn(List.of(buildLegResponseFailed(20L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.process(3L);

        assertThat(result).isFalse();
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.PNDG));
    }

    // ── strategy throws exception → leg recorded as FAILED ────────────────────

    @Test
    void process_strategyThrows_legRecordedAsFailedAndContinues() {
        AccountPostingEntity posting = buildPosting(4L, "FT");
        AccountPostingLegResponseV2 leg = buildLegResponse(30L, 1, "CBS", LegStatus.FAILED);

        PostingStrategy strategy = mock(PostingStrategy.class);
        when(postingRepository.findById(4L)).thenReturn(java.util.Optional.of(posting));
        when(legService.listNonSuccessLegs(4L)).thenReturn(List.of(leg));
        when(strategyFactory.resolve("CBS_POSTING")).thenReturn(strategy);
        when(strategy.process(any(), anyInt(), any(), eq(true), eq(30L)))
                .thenThrow(new RuntimeException("CBS timeout"));
        when(legService.listLegs(4L)).thenReturn(List.of(buildLegResponseFailed(30L)));
        when(postingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.process(4L);

        assertThat(result).isFalse();
        verify(postingRepository).save(argThat(p -> p.getStatus() == PostingStatus.PNDG));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private AccountPostingEntity buildPosting(Long id, String requestType) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(id);
        p.setStatus(PostingStatus.PNDG);
        p.setRequestType(requestType);
        try {
            IncomingPostingRequest req = new IncomingPostingRequest();
            req.setSourceRefId("SRC");
            req.setEndToEndRefId("e2e-" + id);
            req.setSourceName("IMX");
            req.setRequestType("IMX_CBS_GL");
            Amount amount = new Amount();
            amount.setValue("500.00");
            amount.setCurrency("USD");
            req.setAmount(amount);
            req.setCreditDebitIndicator("DEBIT");
            req.setDebtorAccount("ACC-D");
            req.setCreditorAccount("ACC-C");
            req.setRequestedExecutionDate(LocalDate.now().toString());
            p.setRequestPayload(objectMapper.writeValueAsString(req));
        } catch (Exception ignored) {
        }
        return p;
    }

    private AccountPostingLegResponseV2 buildLegResponse(Long legId, int order,
                                                         String targetSystem, LegStatus status) {
        AccountPostingLegResponseV2 r = new AccountPostingLegResponseV2();
        r.setTransactionId(legId);
        r.setTransactionOrder(order);
        r.setTargetSystem(targetSystem);
        r.setOperation("POSTING");
        r.setStatus(status);
        r.setAttemptNumber(1);
        return r;
    }

    private AccountPostingLegResponseV2 buildLegResponseSuccess(Long legId) {
        AccountPostingLegResponseV2 r = new AccountPostingLegResponseV2();
        r.setTransactionId(legId);
        r.setStatus(LegStatus.SUCCESS);
        r.setAttemptNumber(2);
        return r;
    }

    private AccountPostingLegResponseV2 buildLegResponseFailed(Long legId) {
        AccountPostingLegResponseV2 r = new AccountPostingLegResponseV2();
        r.setTransactionId(legId);
        r.setStatus(LegStatus.FAILED);
        r.setAttemptNumber(2);
        return r;
    }

    private LegResponseV2 buildLegResponseV2(String status, Long legId) {
        LegResponseV2 r = new LegResponseV2();
        r.setStatus(status);
        return r;
    }
}
