package com.sr.accountposting.service.processor;

import com.sr.accountposting.dto.ExternalCallResult;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.LegStatus;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.repository.posting.AccountPostingRepository;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.strategy.PostingStrategy;
import com.sr.accountposting.service.strategy.PostingStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostingProcessorServiceImplTest {

    private static final String POSTING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String E2E_REF = "E2E-REF-001";

    @Mock
    AccountPostingRepository postingRepo;
    @Mock
    AccountPostingLegService legService;
    @Mock
    PostingStrategyFactory strategyFactory;
    @Mock
    PostingStrategy cbsStrategy;

    @InjectMocks
    PostingProcessorServiceImpl processor;

    private IncomingPostingRequest request;
    private PostingConfigEntity cbsConfig;
    private AccountPostingEntity posting;

    @BeforeEach
    void setUp() {
        IncomingPostingRequest.Amount amount = new IncomingPostingRequest.Amount();
        amount.setValue("1000.00");
        amount.setCurrencyCode("USD");

        request = new IncomingPostingRequest();
        request.setSourceName("IMX");
        request.setSourceReferenceId("SRC-001");
        request.setEndToEndReferenceId(E2E_REF);
        request.setRequestType("IMX_CBS_GL");
        request.setCreditDebitIndicator("DEBIT");
        request.setDebtorAccount("ACC-001");
        request.setCreditorAccount("ACC-002");
        request.setRequestedExecutionDate("2026-04-19");
        request.setAmount(amount);

        cbsConfig = new PostingConfigEntity();
        cbsConfig.setRequestType("IMX_CBS_GL");
        cbsConfig.setOrderSeq(1);
        cbsConfig.setTargetSystem("CBS");
        cbsConfig.setOperation("POSTING");
        cbsConfig.setSourceName("IMX");
        cbsConfig.setProcessingMode("SYNC");

        posting = new AccountPostingEntity();
        posting.setPostingId(POSTING_ID);
        posting.setStatus(PostingStatus.PNDG.name());
        posting.setEndToEndReferenceId(E2E_REF);
    }

    @Test
    void fullNormFlow_cbsSucceeds_postingMovesToAcsp() {
        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));

        AccountPostingLegEntity cbsLeg = buildLeg(1, "CBS", "POSTING");
        when(legService.listNonSuccessLegs(POSTING_ID)).thenReturn(List.of(cbsLeg));

        when(strategyFactory.get("CBS", "POSTING")).thenReturn(cbsStrategy);
        when(cbsStrategy.process(eq(request), eq(cbsConfig))).thenReturn(
                ExternalCallResult.builder()
                        .status(LegStatus.SUCCESS)
                        .referenceId("CBS-TXN-999")
                        .postedTime("2026-04-19T10:00:00Z")
                        .build()
        );

        PostingJob job = PostingJob.builder()
                .postingId(POSTING_ID)
                .requestPayload(request)
                .requestMode(RequestMode.NORM)
                .build();

        ProcessingResult result = processor.process(job, List.of(cbsConfig));

        assertThat(result.getStatus()).isEqualTo(PostingStatus.ACSP);
        assertThat(result.getReason()).isNull();
        assertThat(result.getFailures()).isEmpty();
        assertThat(result.getUpdatedAt()).isNotNull();

        verify(legService).createLeg(eq(POSTING_ID), eq(1), eq("CBS"), eq("ACC-001"),
                eq("POSTING"), eq("NORM"), anyInt());
        verify(legService).updateLeg(eq(POSTING_ID), eq(1), eq("SUCCESS"),
                eq("CBS-TXN-999"), eq("2026-04-19T10:00:00Z"),
                isNull(), any(), any(), eq(false));

        verify(postingRepo).update(org.mockito.ArgumentMatchers.argThat(p ->
                PostingStatus.ACSP.name().equals(p.getStatus()) && p.getReason() == null));
    }

    @Test
    void fullNormFlow_cbsFails_postingStaysPndgWithFailureRecorded() {
        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));

        AccountPostingLegEntity cbsLeg = buildLeg(1, "CBS", "POSTING");
        when(legService.listNonSuccessLegs(POSTING_ID)).thenReturn(List.of(cbsLeg));

        when(strategyFactory.get("CBS", "POSTING")).thenReturn(cbsStrategy);
        when(cbsStrategy.process(eq(request), eq(cbsConfig))).thenReturn(
                ExternalCallResult.builder()
                        .status(LegStatus.FAILED)
                        .reason("CBS returned status: INSUFFICIENT_FUNDS")
                        .build()
        );

        PostingJob job = PostingJob.builder()
                .postingId(POSTING_ID)
                .requestPayload(request)
                .requestMode(RequestMode.NORM)
                .build();

        ProcessingResult result = processor.process(job, List.of(cbsConfig));

        assertThat(result.getStatus()).isEqualTo(PostingStatus.PNDG);
        assertThat(result.getReason()).contains("INSUFFICIENT_FUNDS");
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getTargetSystem()).isEqualTo("CBS");

        verify(postingRepo).update(org.mockito.ArgumentMatchers.argThat(p ->
                PostingStatus.PNDG.name().equals(p.getStatus())
                        && p.getReason() != null && p.getReason().contains("INSUFFICIENT_FUNDS")));
    }

    @Test
    void retryFlow_existingFailedLeg_reprocessedWithoutCreatingNewLegs() {
        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));

        AccountPostingLegEntity failedLeg = buildLeg(1, "CBS", "POSTING");
        failedLeg.setStatus("FAILED");
        failedLeg.setAttemptNumber(1);
        when(legService.listNonSuccessLegs(POSTING_ID)).thenReturn(List.of(failedLeg));

        when(strategyFactory.get("CBS", "POSTING")).thenReturn(cbsStrategy);
        when(cbsStrategy.process(eq(request), eq(cbsConfig))).thenReturn(
                ExternalCallResult.builder()
                        .status(LegStatus.SUCCESS)
                        .referenceId("CBS-RETRY-001")
                        .build()
        );

        PostingJob job = PostingJob.builder()
                .postingId(POSTING_ID)
                .requestPayload(request)
                .requestMode(RequestMode.RETRY)
                .build();

        ProcessingResult result = processor.process(job, List.of(cbsConfig));

        assertThat(result.getStatus()).isEqualTo(PostingStatus.ACSP);
        assertThat(result.getFailures()).isEmpty();

        verify(legService, never()).createLeg(any(), anyInt(), any(), any(), any(), any(), anyInt());

        verify(legService).updateLeg(eq(POSTING_ID), anyInt(), eq("SUCCESS"),
                eq("CBS-RETRY-001"), any(), any(), any(), any(), eq(true));
    }

    private AccountPostingLegEntity buildLeg(int order, String targetSystem, String operation) {
        AccountPostingLegEntity leg = new AccountPostingLegEntity();
        leg.setPostingId(POSTING_ID);
        leg.setTransactionOrder(order);
        leg.setTargetSystem(targetSystem);
        leg.setOperation(operation);
        leg.setStatus("PNDG");
        leg.setAttemptNumber(1);
        return leg;
    }
}