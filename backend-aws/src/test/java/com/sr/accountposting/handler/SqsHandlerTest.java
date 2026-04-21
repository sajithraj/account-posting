package com.sr.accountposting.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.repository.config.PostingConfigRepository;
import com.sr.accountposting.service.processor.PostingProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String POSTING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String POSTING_ID_2 = "22222222-2222-2222-2222-222222222222";
    private static final String REQUEST_TYPE = "IMX_CBS_GL";

    @Mock
    private PostingProcessorService processor;
    @Mock
    private PostingConfigRepository configRepo;
    @Mock
    private SnsClient snsClient;

    private SqsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SqsHandler(processor, configRepo, snsClient);
    }

    @Test
    void handle_singleRecord_processorInvokedWithJob() throws Exception {
        PostingConfigEntity config = buildConfig(REQUEST_TYPE, 1, "CBS");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(config));
        when(processor.process(any(), any())).thenReturn(acspResult());

        handler.handle(sqsEvent(buildJobJson(POSTING_ID, REQUEST_TYPE)), null);

        verify(processor).process(
                org.mockito.ArgumentMatchers.argThat(job -> job.getPostingId().equals(POSTING_ID)
                        && job.getRequestMode() == RequestMode.NORM),
                org.mockito.ArgumentMatchers.eq(List.of(config))
        );
        verifyNoInteractions(snsClient);
    }

    @Test
    void handle_multipleRecords_eachRecordProcessedIndependently() throws Exception {
        PostingConfigEntity config = buildConfig(REQUEST_TYPE, 1, "CBS");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(config));
        when(processor.process(any(), any())).thenReturn(acspResult());

        List<Map<String, Object>> records = new ArrayList<>();
        records.add(buildRecord("msg-1", buildJobJson(POSTING_ID, REQUEST_TYPE)));
        records.add(buildRecord("msg-2", buildJobJson(POSTING_ID_2, REQUEST_TYPE)));

        handler.handle(Map.of("Records", records), null);

        verify(processor, times(2)).process(any(), any());
    }

    @Test
    void handle_retryModeJob_processorReceivesCorrectMode() throws Exception {
        PostingConfigEntity config = buildConfig(REQUEST_TYPE, 1, "CBS");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(config));
        when(processor.process(any(), any())).thenReturn(acspResult());

        PostingJob job = buildJob(POSTING_ID, REQUEST_TYPE, RequestMode.RETRY);
        handler.handle(sqsEvent(MAPPER.writeValueAsString(job)), null);

        verify(processor).process(
                org.mockito.ArgumentMatchers.argThat(j -> j.getRequestMode() == RequestMode.RETRY),
                any()
        );
    }

    @Test
    void handle_noRecords_processorNeverCalled() {
        handler.handle(Map.of("Records", List.of()), null);
        verifyNoInteractions(processor, snsClient);
    }

    @Test
    void handle_nullRecords_processorNeverCalled() {
        Map<String, Object> event = new HashMap<>();
        event.put("Records", null);
        handler.handle(event, null);
        verifyNoInteractions(processor, snsClient);
    }

    @Test
    void handle_noConfigsForRequestType_processorSkippedAndSnsAlertPublished() throws Exception {
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of());

        handler.handle(sqsEvent(buildJobJson(POSTING_ID, REQUEST_TYPE)), null);

        verifyNoInteractions(processor);
        ArgumentCaptor<PublishRequest> snsCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(snsCaptor.capture());
        assertThat(snsCaptor.getValue().message()).contains(POSTING_ID);
        assertThat(snsCaptor.getValue().message()).contains("No routing configs");
    }

    @Test
    void handle_processorThrowsRuntimeException_exceptionCaughtRemainingRecordsContinue() throws Exception {
        PostingConfigEntity config = buildConfig(REQUEST_TYPE, 1, "CBS");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(config));
        when(processor.process(any(), any())).thenThrow(new RuntimeException("CBS connection timeout"));

        List<Map<String, Object>> records = List.of(
                buildRecord("msg-1", buildJobJson(POSTING_ID, REQUEST_TYPE)),
                buildRecord("msg-2", buildJobJson(POSTING_ID_2, REQUEST_TYPE))
        );

        handler.handle(Map.of("Records", records), null);

        verify(processor, times(2)).process(any(), any());
    }

    @Test
    void handle_processorReturnsFailures_snsAlertPublishedForEachFailure() throws Exception {
        PostingConfigEntity cbsConfig = buildConfig(REQUEST_TYPE, 1, "CBS");
        PostingConfigEntity glConfig = buildConfig(REQUEST_TYPE, 2, "GL");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(cbsConfig, glConfig));

        ProcessingResult failedResult = ProcessingResult.builder()
                .status(PostingStatus.PNDG)
                .reason("CBS: INSUFFICIENT_FUNDS; GL: CBS_FAILED")
                .failures(List.of(
                        ProcessingResult.LegFailure.builder().targetSystem("CBS").reason("INSUFFICIENT_FUNDS").build(),
                        ProcessingResult.LegFailure.builder().targetSystem("GL").reason("CBS_FAILED").build()
                ))
                .updatedAt("2026-04-21T10:00:00Z")
                .build();
        when(processor.process(any(), any())).thenReturn(failedResult);

        handler.handle(sqsEvent(buildJobJson(POSTING_ID, REQUEST_TYPE)), null);

        verify(snsClient, times(2)).publish(any(PublishRequest.class));
    }

    @Test
    void handle_processorReturnsAcspWithEmptyFailures_noSnsAlert() throws Exception {
        PostingConfigEntity config = buildConfig(REQUEST_TYPE, 1, "CBS");
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of(config));
        when(processor.process(any(), any())).thenReturn(acspResult());

        handler.handle(sqsEvent(buildJobJson(POSTING_ID, REQUEST_TYPE)), null);

        verifyNoInteractions(snsClient);
    }

    @Test
    void handle_snsPublishFails_noExceptionPropagated() throws Exception {
        when(configRepo.findByRequestType(REQUEST_TYPE)).thenReturn(List.of());
        doThrow(new RuntimeException("SNS unavailable")).when(snsClient).publish(any(PublishRequest.class));

        handler.handle(sqsEvent(buildJobJson(POSTING_ID, REQUEST_TYPE)), null);
    }

    private Map<String, Object> sqsEvent(String bodyJson) {
        return Map.of("Records", List.of(buildRecord("msg-001", bodyJson)));
    }

    private Map<String, Object> buildRecord(String messageId, String bodyJson) {
        Map<String, Object> record = new HashMap<>();
        record.put("eventSource", "aws:sqs");
        record.put("messageId", messageId);
        record.put("body", bodyJson);
        return record;
    }

    private String buildJobJson(String postingId, String requestType) throws Exception {
        return MAPPER.writeValueAsString(buildJob(postingId, requestType, RequestMode.NORM));
    }

    private PostingJob buildJob(String postingId, String requestType, RequestMode mode) {
        IncomingPostingRequest request = new IncomingPostingRequest();
        request.setSourceName("IMX");
        request.setSourceReferenceId("SRC-" + postingId);
        request.setEndToEndReferenceId("E2E-" + postingId);
        request.setRequestType(requestType);
        request.setCreditDebitIndicator("DEBIT");
        request.setDebtorAccount("1000123456");
        request.setCreditorAccount("1000654321");

        IncomingPostingRequest.Amount amount = new IncomingPostingRequest.Amount();
        amount.setValue("1000.00");
        amount.setCurrencyCode("USD");
        request.setAmount(amount);

        return PostingJob.builder()
                .postingId(postingId)
                .requestPayload(request)
                .requestMode(mode)
                .build();
    }

    private PostingConfigEntity buildConfig(String requestType, int orderSeq, String targetSystem) {
        PostingConfigEntity config = new PostingConfigEntity();
        config.setRequestType(requestType);
        config.setOrderSeq(orderSeq);
        config.setTargetSystem(targetSystem);
        config.setOperation("POSTING");
        config.setProcessingMode("ASYNC");
        return config;
    }

    private ProcessingResult acspResult() {
        return ProcessingResult.builder()
                .status(PostingStatus.ACSP)
                .failures(List.of())
                .updatedAt("2026-04-21T10:00:00Z")
                .build();
    }
}