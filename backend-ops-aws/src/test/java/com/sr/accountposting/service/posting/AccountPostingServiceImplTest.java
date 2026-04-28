package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.PostingSearchResponse;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import com.sr.accountposting.repository.posting.AccountPostingRepository;
import com.sr.accountposting.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPostingServiceImplTest {

    private static final String POSTING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String POSTING_ID_2 = "22222222-2222-2222-2222-222222222222";
    private static final String POSTING_ID_3 = "33333333-3333-3333-3333-333333333333";
    private static final String MISSING_POSTING_ID = "99999999-9999-9999-9999-999999999999";

    @Mock
    private AccountPostingRepository postingRepo;
    @Mock
    private AccountPostingLegRepository legRepo;
    @Mock
    private SqsClient sqsClient;

    private AccountPostingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountPostingServiceImpl(postingRepo, legRepo, sqsClient);
    }

    @Test
    void findById_existingPosting_returnsPostingWithLegs() {
        AccountPostingEntity posting = buildPosting(POSTING_ID, PostingStatus.ACSP, "E2E-001", "IMX");
        AccountPostingLegEntity cbsLeg = buildLeg(POSTING_ID, 1, "CBS");
        AccountPostingLegEntity glLeg = buildLeg(POSTING_ID, 2, "GL");

        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of(cbsLeg, glLeg));

        PostingResponse response = service.findById(POSTING_ID);

        assertThat(response.getEndToEndReferenceId()).isEqualTo("E2E-001");
        assertThat(response.getPostingStatus()).isEqualTo(PostingStatus.ACSP.name());
        assertThat(response.getLegs()).hasSize(2);
        assertThat(response.getLegs().get(0).getTargetSystem()).isEqualTo("CBS");
        assertThat(response.getLegs().get(1).getTargetSystem()).isEqualTo("GL");
    }

    @Test
    void findById_existingPosting_legsIncludedInResponse() {
        AccountPostingEntity posting = buildPosting(POSTING_ID, PostingStatus.PNDG, "E2E-PNDG", "RMS");
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "OBPM");
        leg.setStatus("FAILED");
        leg.setReason("OBPM connection timeout");

        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of(leg));

        PostingResponse response = service.findById(POSTING_ID);

        assertThat(response.getLegs()).hasSize(1);
        assertThat(response.getLegs().get(0).getStatus()).isEqualTo("FAILED");
        assertThat(response.getLegs().get(0).getReason()).isEqualTo("OBPM connection timeout");
    }

    @Test
    void findById_missingPosting_throwsResourceNotFoundException() {
        when(postingRepo.findById(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(MISSING_POSTING_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(MISSING_POSTING_ID);
    }

    @Test
    void search_byStatus_returnsMatchingPostings() {
        AccountPostingEntity p1 = buildPosting(POSTING_ID, PostingStatus.PNDG, "E2E-001", "IMX");
        AccountPostingEntity p2 = buildPosting(POSTING_ID_2, PostingStatus.PNDG, "E2E-002", "IMX");

        when(postingRepo.search(eq("PNDG"), any(), any(), any(), any(), any(), any(), eq(10), any()))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(p1, p2), "NEXT"));
        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of());
        when(legRepo.findByPostingId(POSTING_ID_2)).thenReturn(List.of());

        PostingSearchRequest req = new PostingSearchRequest();
        req.setStatus("PNDG");
        req.setLimit(10);

        PostingSearchResponse results = service.search(req);

        assertThat(results.getItems()).hasSize(2);
        assertThat(results.getNextPageToken()).isEqualTo("NEXT");
        assertThat(results.getItems()).extracting(PostingResponse::getPostingStatus)
                .containsOnly(PostingStatus.PNDG.name());
    }

    @Test
    void search_bySourceName_returnsMatchingPostings() {
        AccountPostingEntity p = buildPosting(POSTING_ID, PostingStatus.ACSP, "E2E-001", "RMS");

        when(postingRepo.search(any(), eq("RMS"), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(p), null));
        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of());

        PostingSearchRequest req = new PostingSearchRequest();
        req.setSourceName("RMS");

        PostingSearchResponse results = service.search(req);

        assertThat(results.getItems()).hasSize(1);
        assertThat(results.getItems().get(0).getSourceReferenceId()).isEqualTo("SRC-" + POSTING_ID);
    }

    @Test
    void search_defaultLimit_uses20WhenNotSpecified() {
        when(postingRepo.search(any(), any(), any(), any(), any(), any(), any(), eq(20), any()))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(), null));

        service.search(new PostingSearchRequest());

        verify(postingRepo).search(any(), any(), any(), any(), any(), any(), any(), eq(20), any());
    }

    @Test
    void search_pageToken_passedToRepository() {
        when(postingRepo.search(any(), any(), any(), any(), any(), any(), any(), eq(20), eq("TOKEN")))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(), null));

        PostingSearchRequest req = new PostingSearchRequest();
        req.setPageToken("TOKEN");

        service.search(req);

        verify(postingRepo).search(any(), any(), any(), any(), any(), any(), any(), eq(20), eq("TOKEN"));
    }

    @Test
    void search_allFilters_passedToRepository() {
        when(postingRepo.search(eq("ACSP"), eq("IMX"), eq("IMX_CBS_GL"), eq("E2E-001"),
                eq("SRC-001"), eq("2026-04-01T00:00:00Z"), eq("2026-04-30T23:59:59Z"), eq(15), any()))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(), null));

        PostingSearchRequest req = new PostingSearchRequest();
        req.setStatus("ACSP");
        req.setSourceName("IMX");
        req.setRequestType("IMX_CBS_GL");
        req.setEndToEndReferenceId("E2E-001");
        req.setSourceReferenceId("SRC-001");
        req.setFromDate("2026-04-01T00:00:00Z");
        req.setToDate("2026-04-30T23:59:59Z");
        req.setLimit(15);

        service.search(req);

        verify(postingRepo).search(eq("ACSP"), eq("IMX"), eq("IMX_CBS_GL"), eq("E2E-001"),
                eq("SRC-001"), eq("2026-04-01T00:00:00Z"), eq("2026-04-30T23:59:59Z"), eq(15), any());
    }

    @Test
    void search_invalidLimit_throwsValidationException() {
        PostingSearchRequest req = new PostingSearchRequest();
        req.setLimit(0);

        assertThatThrownBy(() -> service.search(req))
                .hasMessageContaining("limit must be between 1 and 200");
    }

    @Test
    void search_noResults_returnsEmptyList() {
        when(postingRepo.search(any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(new AccountPostingRepository.SearchResult(List.of(), null));

        PostingSearchResponse results = service.search(new PostingSearchRequest());

        assertThat(results.getItems()).isEmpty();
        assertThat(results.getNextPageToken()).isNull();
    }

    @Test
    void retry_withSpecificIds_locksAndPublishesJobForEach() throws Exception {
        IncomingPostingRequest originalReq = buildRequest("IMX_CBS_GL", "E2E-001");
        AccountPostingEntity posting = buildPosting(POSTING_ID, PostingStatus.PNDG, "E2E-001", "IMX");
        posting.setRequestPayload(JsonUtil.toJson(originalReq));

        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(postingRepo.acquireRetryLock(eq(POSTING_ID), anyLong())).thenReturn(true);

        RetryRequest req = new RetryRequest();
        req.setPostingIds(List.of(POSTING_ID));
        req.setRequestedBy("ops-admin");

        RetryResponse response = service.retry(req);

        assertThat(response.getTotalPostings()).isEqualTo(1);
        assertThat(response.getQueued()).isEqualTo(1);
        assertThat(response.getSkippedLocked()).isEqualTo(0);

        ArgumentCaptor<SendMessageRequest> sqsCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(sqsCaptor.capture());

        PostingJob sentJob = JsonUtil.fromJson(sqsCaptor.getValue().messageBody(), PostingJob.class);
        assertThat(sentJob.getPostingId()).isEqualTo(POSTING_ID);
        assertThat(sentJob.getRequestMode()).isEqualTo(RequestMode.RETRY);
    }

    @Test
    void retry_withNoIds_scansAllPndgAndReceived() {
        AccountPostingEntity pndg1 = buildPosting(POSTING_ID, PostingStatus.PNDG, "E2E-001", "IMX");
        AccountPostingEntity pndg2 = buildPosting(POSTING_ID_2, PostingStatus.PNDG, "E2E-002", "IMX");
        AccountPostingEntity received = buildPosting(POSTING_ID_3, PostingStatus.RCVD, "E2E-003", "RMS");

        for (AccountPostingEntity p : List.of(pndg1, pndg2, received)) {
            p.setRequestPayload(JsonUtil.toJson(buildRequest("IMX_CBS_GL", p.getEndToEndReferenceId())));
        }

        when(postingRepo.findByStatus(PostingStatus.PNDG.name())).thenReturn(List.of(pndg1, pndg2));
        when(postingRepo.findByStatus(PostingStatus.RCVD.name())).thenReturn(List.of(received));
        when(postingRepo.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if (id.equals(POSTING_ID)) {
                return Optional.of(pndg1);
            }
            if (id.equals(POSTING_ID_2)) {
                return Optional.of(pndg2);
            }
            if (id.equals(POSTING_ID_3)) {
                return Optional.of(received);
            }
            return Optional.empty();
        });
        when(postingRepo.acquireRetryLock(anyString(), anyLong())).thenReturn(true);

        RetryRequest req = new RetryRequest();
        req.setRequestedBy("ops-admin");

        RetryResponse response = service.retry(req);

        assertThat(response.getTotalPostings()).isEqualTo(3);
        assertThat(response.getQueued()).isEqualTo(3);
        assertThat(response.getSkippedLocked()).isEqualTo(0);
        verify(sqsClient, times(3)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void retry_lockNotAcquired_postingSkipped() {
        AccountPostingEntity posting = buildPosting(POSTING_ID, PostingStatus.PNDG, "E2E-001", "IMX");

        when(postingRepo.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(postingRepo.acquireRetryLock(eq(POSTING_ID), anyLong())).thenReturn(false);

        RetryRequest req = new RetryRequest();
        req.setPostingIds(List.of(POSTING_ID));
        req.setRequestedBy("ops-admin");

        RetryResponse response = service.retry(req);

        assertThat(response.getQueued()).isEqualTo(0);
        assertThat(response.getSkippedLocked()).isEqualTo(1);
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void retry_postingNotFound_skippedWithoutError() {
        when(postingRepo.findById(anyString())).thenReturn(Optional.empty());

        RetryRequest req = new RetryRequest();
        req.setPostingIds(List.of(MISSING_POSTING_ID));
        req.setRequestedBy("ops-admin");

        RetryResponse response = service.retry(req);

        assertThat(response.getQueued()).isEqualTo(0);
        assertThat(response.getSkippedLocked()).isEqualTo(1);
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    private AccountPostingEntity buildPosting(String id, PostingStatus status, String e2eRef, String source) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(id);
        p.setStatus(status.name());
        p.setEndToEndReferenceId(e2eRef);
        p.setSourceName(source);
        p.setSourceReferenceId("SRC-" + id);
        p.setCreatedAt("2026-04-21T10:00:00Z");
        p.setUpdatedAt("2026-04-21T10:00:00Z");
        return p;
    }

    private AccountPostingLegEntity buildLeg(String postingId, int order, String targetSystem) {
        AccountPostingLegEntity leg = new AccountPostingLegEntity();
        leg.setPostingId(postingId);
        leg.setTransactionOrder(order);
        leg.setTargetSystem(targetSystem);
        leg.setStatus("ACSP");
        leg.setOperation("POSTING");
        leg.setMode("NORM");
        return leg;
    }

    private IncomingPostingRequest buildRequest(String requestType, String e2eRef) {
        IncomingPostingRequest req = new IncomingPostingRequest();
        req.setSourceName("IMX");
        req.setSourceReferenceId("SRC-001");
        req.setEndToEndReferenceId(e2eRef);
        req.setRequestType(requestType);
        req.setCreditDebitIndicator("DEBIT");
        req.setDebtorAccount("1000123456");
        req.setCreditorAccount("1000654321");
        IncomingPostingRequest.Amount amount = new IncomingPostingRequest.Amount();
        amount.setValue("1000.00");
        amount.setCurrencyCode("USD");
        req.setAmount(amount);
        return req;
    }
}
