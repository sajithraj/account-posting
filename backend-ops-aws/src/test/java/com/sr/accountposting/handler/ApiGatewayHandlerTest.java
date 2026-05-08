package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.PostingSearchResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiGatewayHandlerTest {

    private static final String BASE = "/v3/payment/account-posting";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String POSTING_ID = "11111111-1111-1111-1111-111111111111";
    private static final String MISSING_POSTING_ID = "99999999-9999-9999-9999-999999999999";
    private static final String TRANSACTION_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final int TRANSACTION_ORDER = 1;

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

    @Test
    void search_validRequest_returns200WithList() throws Exception {
        PostingResponse p = PostingResponse.builder()
                .endToEndReferenceId("E2E-001")
                .postingStatus(PostingStatus.PNDG.name())
                .build();
        when(postingService.search(any(PostingSearchRequest.class))).thenReturn(
                PostingSearchResponse.builder().items(List.of(p)).nextPageToken("NEXT").build());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{\"status\":\"PNDG\",\"limit\":10}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat((List<?>) body.get("items")).hasSize(1);
        assertThat(body.get("next_page_token")).isEqualTo("NEXT");
        verify(postingService).search(any());
    }

    @Test
    void search_emptyBody_returns200WithEmptyList() throws Exception {
        when(postingService.search(any())).thenReturn(
                PostingSearchResponse.builder().items(List.of()).build());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat((List<?>) body.get("items")).isEmpty();
    }

    @Test
    void search_noResults_returns200WithEmptyList() throws Exception {
        when(postingService.search(any())).thenReturn(
                PostingSearchResponse.builder().items(List.of()).build());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{\"source_name\":\"UNKNOWN\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat((List<?>) body.get("items")).isEmpty();
    }

    @Test
    void search_requestBodyMapsToSearchRequest() throws Exception {
        when(postingService.search(any())).thenReturn(
                PostingSearchResponse.builder().items(List.of()).build());

        String body = "{\"end_to_end_id\":\"E2E-001\",\"source_ref_id\":\"SRC-001\","
                + "\"source_name\":\"IMX\",\"status\":\"ACSP\",\"request_type\":\"IMX_CBS_GL\","
                + "\"from_date\":\"2026-04-01T00:00:00Z\",\"to_date\":\"2026-04-30T23:59:59Z\",\"limit\":10}";
        handler.handle(apiEvent("POST", BASE + "/search", body), null);

        ArgumentCaptor<PostingSearchRequest> captor = ArgumentCaptor.forClass(PostingSearchRequest.class);
        verify(postingService).search(captor.capture());
        assertThat(captor.getValue().getEndToEndReferenceId()).isEqualTo("E2E-001");
        assertThat(captor.getValue().getSourceReferenceId()).isEqualTo("SRC-001");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACSP");
        assertThat(captor.getValue().getLimit()).isEqualTo(10);
    }

    @Test
    void findById_existingPosting_returns200WithPosting() throws Exception {
        PostingResponse p = PostingResponse.builder()
                .endToEndReferenceId("E2E-999")
                .postingStatus(PostingStatus.ACSP.name())
                .build();
        when(postingService.findById(POSTING_ID)).thenReturn(p);

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/" + POSTING_ID, null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("posting_status")).isEqualTo("ACSP");
        verify(postingService).findById(POSTING_ID);
    }

    @Test
    void findById_missingPosting_returns404() {
        when(postingService.findById(anyString())).thenThrow(
                new ResourceNotFoundException("Posting not found: " + MISSING_POSTING_ID));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/" + MISSING_POSTING_ID, null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void retry_missingPostingIds_returns400() throws Exception {
        when(postingService.retry(any(RetryRequest.class))).thenThrow(
                new ValidationException("POSTING_IDS_REQUIRED",
                        "posting_ids must be provided for retry; bulk retry is not allowed"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", "{\"requested_by\":\"ops-admin\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo("POSTING_IDS_REQUIRED");
    }

    @Test
    void retry_withSpecificIds_returns200() throws Exception {
        when(postingService.retry(any())).thenReturn(
                RetryResponse.builder().totalPostings(2).queued(2).skippedLocked(0).build());

        String body = "{\"posting_ids\":[\"11111111-1111-1111-1111-111111111111\",\"22222222-2222-2222-2222-222222222222\"],\"requested_by\":\"admin\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> responseBody = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(responseBody.get("queued")).isEqualTo(2);
    }

    @Test
    void manualUpdateLeg_validRequest_returns200WithUpdatedLeg() throws Exception {
        LegResponse updated = LegResponse.builder()
                .postingId(POSTING_ID)
                .transactionId(TRANSACTION_ID)
                .transactionOrder(TRANSACTION_ORDER)
                .status("SUCCESS")
                .mode("MANUAL")
                .reason("Manually resolved by ops")
                .build();
        doNothing().when(legService).manualUpdateLeg(anyString(), anyInt(), anyString(), anyString(), anyString());
        when(legService.getLeg(POSTING_ID, TRANSACTION_ORDER)).thenReturn(updated);

        String body = "{\"status\":\"SUCCESS\",\"reason\":\"Manually resolved by ops\",\"requested_by\":\"ops-admin\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PATCH", BASE + "/" + POSTING_ID + "/transaction/" + TRANSACTION_ORDER, body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> responseBody = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(responseBody.get("status")).isEqualTo("SUCCESS");
        assertThat(responseBody.get("mode")).isEqualTo("MANUAL");
        verify(legService).manualUpdateLeg(eq(POSTING_ID), eq(TRANSACTION_ORDER), eq("SUCCESS"),
                eq("Manually resolved by ops"), eq("ops-admin"));
    }

    @Test
    void manualUpdateLeg_legNotFound_returns404() {
        doThrow(new ResourceNotFoundException("Leg not found"))
                .when(legService).manualUpdateLeg(anyString(), anyInt(), anyString(), anyString(), anyString());

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PATCH", BASE + "/" + POSTING_ID + "/transaction/" + TRANSACTION_ORDER,
                        "{\"status\":\"SUCCESS\",\"reason\":\"test\",\"requested_by\":\"admin\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void getConfig_allConfigs_returns200() throws Exception {
        when(configService.getAll()).thenReturn(List.of(
                buildConfig("IMX_CBS_GL", 1, "CBS"),
                buildConfig("IMX_CBS_GL", 2, "GL")));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/config", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        List<?> body = MAPPER.readValue(response.getBody(), List.class);
        assertThat(body).hasSize(2);
        verify(configService).getAll();
    }

    @Test
    void getConfig_byRequestType_returns200() throws Exception {
        when(configService.getByRequestType("IMX_CBS_GL"))
                .thenReturn(List.of(buildConfig("IMX_CBS_GL", 1, "CBS")));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("GET", BASE + "/config/IMX_CBS_GL", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        List<?> body = MAPPER.readValue(response.getBody(), List.class);
        assertThat(body).hasSize(1);
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
        Map<?, ?> responseBody = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(responseBody.get("request_type")).isEqualTo("NEW_TYPE");
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

        String body = "{\"target_system\":\"CBS\",\"operation\":\"POSTING_V2\",\"processing_mode\":\"ASYNC\"}";
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PUT", BASE + "/config/IMX_CBS_GL/1", body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> responseBody = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(responseBody.get("operation")).isEqualTo("POSTING_V2");
        verify(configService).update(eq("IMX_CBS_GL"), eq(1), any());
    }

    @Test
    void updateConfig_notFound_returns404() {
        when(configService.update(anyString(), anyInt(), any())).thenThrow(
                new ResourceNotFoundException("Config not found: requestType=IMX_CBS_GL orderSeq=1"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PUT", BASE + "/config/IMX_CBS_GL/1",
                        "{\"target_system\":\"CBS\",\"operation\":\"POSTING\",\"processing_mode\":\"ASYNC\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void updateConfig_nonIntegerOrderSeq_returns400() throws Exception {
        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("PUT", BASE + "/config/IMX_CBS_GL/not-a-number",
                        "{\"target_system\":\"CBS\",\"operation\":\"POSTING\",\"processing_mode\":\"ASYNC\"}"), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo("INVALID_ORDER_SEQ");
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
                apiEvent("DELETE", BASE + "/config/IMX_CBS_GL/1", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

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

    @Test
    void technicalException_returns500() throws Exception {
        when(postingService.search(any())).thenThrow(
                new TechnicalException("DynamoDB connection refused"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/search", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(500);
        assertThat(MAPPER.readValue(response.getBody(), Map.class).get("name")).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void validationException_returns400() {
        when(postingService.retry(any())).thenThrow(
                new ValidationException("POSTING_IDS_REQUIRED",
                        "posting_ids must be provided for retry; bulk retry is not allowed"));

        APIGatewayV2HTTPResponse response = handler.handle(
                apiEvent("POST", BASE + "/retry", "{}"), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

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
