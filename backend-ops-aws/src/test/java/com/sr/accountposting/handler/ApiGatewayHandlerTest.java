package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.exception.TechnicalException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.service.config.PostingConfigService;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.posting.AccountPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiGatewayHandlerTest {

    private static final String BASE = "/v2/payment/account-posting";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AccountPostingService postingService;
    @Mock
    private AccountPostingLegService legService;
    @Mock
    private PostingConfigService configService;

    private ApiGatewayHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiGatewayHandler(postingService, legService, configService);
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    @Test
    void search_validRequest_returns200WithList() throws Exception {
        PostingResponse p = PostingResponse.builder()
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.PNDG.name())
                .build();
        when(postingService.search(any(PostingSearchRequest.class))).thenReturn(List.of(p));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{\"status\":\"PNDG\",\"limit\":10}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("success")).isEqualTo(true);
        assertThat((List<?>) body.get("data")).hasSize(1);
        verify(postingService).search(any());
    }

    @Test
    void search_noResults_returns200WithEmptyList() throws Exception {
        when(postingService.search(any())).thenReturn(List.of());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{\"source_name\":\"UNKNOWN\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat((List<?>) MAPPER.readValue(response.getBody(), Map.class).get("data")).isEmpty();
    }

    // ─── Find by ID ───────────────────────────────────────────────────────────

    @Test
    void findById_existingPosting_returns200WithPosting() throws Exception {
        PostingResponse p = PostingResponse.builder()
                .endToEndReferenceId("E2E-999")
                .postingStatus(PostingStatus.ACSP.name())
                .build();
        when(postingService.findById(123456L)).thenReturn(p);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/123456", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data.get("posting_status")).isEqualTo("ACSP");
        verify(postingService).findById(123456L);
    }

    @Test
    void findById_missingPosting_returns404() {
        when(postingService.findById(anyLong())).thenThrow(
                new ResourceNotFoundException("Posting not found: 999999"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/999999", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    // ─── Retry ────────────────────────────────────────────────────────────────

    @Test
    void retry_allPending_returns200WithRetryResponse() throws Exception {
        RetryResponse retryResp = RetryResponse.builder()
                .totalPostings(5)
                .queued(3)
                .skippedLocked(2)
                .message("Retry processing submitted.")
                .build();
        when(postingService.retry(any(RetryRequest.class))).thenReturn(retryResp);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", "{\"requested_by\":\"ops-admin\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data.get("total_postings")).isEqualTo(5);
        assertThat(data.get("queued")).isEqualTo(3);
        assertThat(data.get("skipped_locked")).isEqualTo(2);
    }

    @Test
    void retry_withSpecificIds_returns200() throws Exception {
        when(postingService.retry(any())).thenReturn(
                RetryResponse.builder().totalPostings(2).queued(2).skippedLocked(0).build());

        String body = "{\"posting_ids\":[100001,100002],\"requested_by\":\"admin\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data.get("queued")).isEqualTo(2);
    }

    // ─── Legs ─────────────────────────────────────────────────────────────────

    @Test
    void listLegs_existingPosting_returns200WithLegList() throws Exception {
        LegResponse leg = LegResponse.builder()
                .postingId(123456L).transactionOrder(1)
                .targetSystem("CBS").status("ACSP")
                .build();
        when(legService.listLegs(123456L)).thenReturn(List.of(leg));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/123456/transaction", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        List<?> data = (List<?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data).hasSize(1);
        verify(legService).listLegs(123456L);
    }

    @Test
    void getLeg_specificOrder_returns200() throws Exception {
        LegResponse leg = LegResponse.builder()
                .postingId(123456L).transactionOrder(1)
                .targetSystem("GL").status("ACSP")
                .referenceId("GL-TXN-001")
                .build();
        when(legService.getLeg(123456L, 1)).thenReturn(leg);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/123456/transaction/1", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data.get("target_system")).isEqualTo("GL");
        assertThat(data.get("reference_id")).isEqualTo("GL-TXN-001");
    }

    @Test
    void getLeg_missingLeg_returns404() {
        when(legService.getLeg(anyLong(), anyInt())).thenThrow(
                new ResourceNotFoundException("Leg not found postingId=123456 order=99"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/123456/transaction/99", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void manualUpdateLeg_validRequest_returns200WithUpdatedLeg() throws Exception {
        LegResponse updated = LegResponse.builder()
                .postingId(123456L).transactionOrder(1)
                .status("SUCCESS").mode("MANUAL")
                .reason("Manually resolved by ops")
                .build();
        doNothing().when(legService).manualUpdateLeg(anyLong(), anyInt(), anyString(), anyString(), anyString());
        when(legService.getLeg(123456L, 1)).thenReturn(updated);

        String body = "{\"status\":\"SUCCESS\",\"reason\":\"Manually resolved by ops\",\"requested_by\":\"ops-admin\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PATCH", BASE + "/123456/transaction/1", body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> data = (Map<?, ?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data.get("status")).isEqualTo("SUCCESS");
        assertThat(data.get("mode")).isEqualTo("MANUAL");
        verify(legService).manualUpdateLeg(eq(123456L), eq(1), eq("SUCCESS"),
                eq("Manually resolved by ops"), eq("ops-admin"));
    }

    @Test
    void manualUpdateLeg_legNotFound_returns404() {
        doThrow(new ResourceNotFoundException("Leg not found"))
                .when(legService).manualUpdateLeg(anyLong(), anyInt(), anyString(), anyString(), anyString());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PATCH", BASE + "/123456/transaction/99",
                        "{\"status\":\"SUCCESS\",\"reason\":\"test\",\"requested_by\":\"admin\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    @Test
    void getConfig_allConfigs_returns200() throws Exception {
        when(configService.getAll()).thenReturn(List.of(
                buildConfig("IMX_CBS_GL", 1, "CBS"),
                buildConfig("IMX_CBS_GL", 2, "GL")));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/config", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        List<?> data = (List<?>) MAPPER.readValue(response.getBody(), Map.class).get("data");
        assertThat(data).hasSize(2);
        verify(configService).getAll();
    }

    @Test
    void getConfig_byRequestType_returns200() throws Exception {
        when(configService.getByRequestType("IMX_CBS_GL"))
                .thenReturn(List.of(buildConfig("IMX_CBS_GL", 1, "CBS")));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/config/IMX_CBS_GL", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(configService).getByRequestType("IMX_CBS_GL");
    }

    @Test
    void createConfig_validRequest_returns201() throws Exception {
        when(configService.create(any())).thenReturn(buildConfig("NEW_TYPE", 1, "CBS"));

        String body = "{\"request_type\":\"NEW_TYPE\",\"order_seq\":1,\"target_system\":\"CBS\","
                + "\"operation\":\"POSTING\",\"processing_mode\":\"ASYNC\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/config", body), null);

        assertThat(response.getStatusCode()).isEqualTo(201);
        verify(configService).create(any());
    }

    @Test
    void createConfig_duplicate_returns422() {
        when(configService.create(any())).thenThrow(
                new BusinessException("DUPLICATE_CONFIG", "Config already exists for requestType=IMX_CBS_GL orderSeq=1"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/config", "{\"request_type\":\"IMX_CBS_GL\",\"order_seq\":1}"), null);

        assertThat(response.getStatusCode()).isEqualTo(422);
    }

    @Test
    void updateConfig_validRequest_returns200() throws Exception {
        PostingConfigEntity updated = buildConfig("IMX_CBS_GL", 1, "CBS");
        updated.setOperation("POSTING_V2");
        when(configService.update(eq("IMX_CBS_GL"), eq(1), any())).thenReturn(updated);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PUT", BASE + "/config/IMX_CBS_GL/1", "{\"target_system\":\"CBS\",\"operation\":\"POSTING_V2\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(configService).update(eq("IMX_CBS_GL"), eq(1), any());
    }

    @Test
    void updateConfig_notFound_returns404() {
        when(configService.update(anyString(), anyInt(), any())).thenThrow(
                new ResourceNotFoundException("Config not found: requestType=MISSING orderSeq=1"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PUT", BASE + "/config/MISSING/1", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void deleteConfig_existingConfig_returns204() {
        doNothing().when(configService).delete("IMX_CBS_GL", 1);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("DELETE", BASE + "/config/IMX_CBS_GL/1", null), null);

        assertThat(response.getStatusCode()).isEqualTo(204);
        verify(configService).delete("IMX_CBS_GL", 1);
    }

    @Test
    void deleteConfig_notFound_returns404() {
        doThrow(new ResourceNotFoundException("Config not found"))
                .when(configService).delete(anyString(), anyInt());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("DELETE", BASE + "/config/MISSING/99", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    // ─── Routes not in backend-ops-aws ───────────────────────────────────────

    @Test
    void createPosting_notHandledByOpsLambda_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE, "{\"source_name\":\"IMX\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void unknownRoute_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", "/completely/unknown", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    // ─── Exception handling ───────────────────────────────────────────────────

    @Test
    void technicalException_returns500() throws Exception {
        when(postingService.search(any())).thenThrow(
                new TechnicalException("DynamoDB connection refused"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(MAPPER.readValue(response.getBody(), Map.class).get("success")).isEqualTo(false);
    }

    @Test
    void validationException_returns400() {
        when(postingService.retry(any())).thenThrow(
                new ValidationException("INVALID_FIELD", "posting_ids must not be empty"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static APIGatewayV2HTTPEvent apiEvent(String method, String path, String body) {
        APIGatewayV2HTTPEvent.RequestContext.Http http = APIGatewayV2HTTPEvent.RequestContext.Http.builder()
                .withMethod(method)
                .withPath(path)
                .build();
        APIGatewayV2HTTPEvent.RequestContext requestContext = APIGatewayV2HTTPEvent.RequestContext.builder()
                .withHttp(http)
                .build();
        return APIGatewayV2HTTPEvent.builder()
                .withRequestContext(requestContext)
                .withRawPath(path)
                .withBody(body)
                .build();
    }

    private static PostingConfigEntity buildConfig(String requestType, int orderSeq, String targetSystem) {
        PostingConfigEntity c = new PostingConfigEntity();
        c.setRequestType(requestType);
        c.setOrderSeq(orderSeq);
        c.setTargetSystem(targetSystem);
        c.setOperation("POSTING");
        c.setProcessingMode("ASYNC");
        return c;
    }
}
