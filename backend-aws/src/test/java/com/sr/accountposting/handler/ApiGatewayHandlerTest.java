package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.TechnicalException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.service.posting.AccountPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiGatewayHandlerTest {

    private static final String BASE = "/v3/payment/account-posting";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AccountPostingService postingService;

    private ApiGatewayHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiGatewayHandler(postingService);
    }

    // ─── POST create ─────────────────────────────────────────────────────────

    @Test
    void createPosting_validRequest_returns200WithPostingResponse() throws Exception {
        PostingResponse posting = PostingResponse.builder()
                .endToEndReferenceId("E2E-001")
                .sourceReferenceId("SRC-001")
                .postingStatus(PostingStatus.ACSP.name())
                .processedAt("2026-04-21T10:00:00Z")
                .build();
        when(postingService.create(any(IncomingPostingRequest.class))).thenReturn(posting);

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("end_to_end_reference_id")).isEqualTo("E2E-001");
        assertThat(body.get("posting_status")).isEqualTo("ACSP");
        verify(postingService).create(any(IncomingPostingRequest.class));
    }

    @Test
    void createPosting_asyncAccepted_returnsAcspStatus() throws Exception {
        when(postingService.create(any())).thenReturn(PostingResponse.builder()
                .postingStatus(PostingStatus.ACSP.name())
                .endToEndReferenceId("E2E-ASYNC")
                .build());

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("posting_status")).isEqualTo("ACSP");
    }

    @Test
    void createPosting_syncProcessed_returnsPndgOrAcspStatus() throws Exception {
        when(postingService.create(any())).thenReturn(PostingResponse.builder()
                .postingStatus(PostingStatus.PNDG.name())
                .endToEndReferenceId("E2E-SYNC")
                .build());

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("posting_status")).isEqualTo("PNDG");
    }

    @Test
    void createPosting_duplicateE2eRef_returns422() throws Exception {
        when(postingService.create(any())).thenThrow(
                new BusinessException("DUPLICATE_E2E_REF", "Posting already exists for endToEndReferenceId: E2E-001"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(422);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo("DUPLICATE_E2E_REF");
    }

    @Test
    void createPosting_unknownRequestType_returns400() throws Exception {
        when(postingService.create(any())).thenThrow(
                new ValidationException("UNKNOWN_REQUEST_TYPE", "Unknown or unconfigured requestType: DOES_NOT_EXIST"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo("UNKNOWN_REQUEST_TYPE");
    }

    @Test
    void createPosting_missingAmount_returns400() throws Exception {
        when(postingService.create(any())).thenThrow(
                new ValidationException("MISSING_AMOUNT", "Amount is required"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void createPosting_technicalException_returns500() throws Exception {
        when(postingService.create(any())).thenThrow(
                new TechnicalException("Failed to persist posting"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(500);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("name")).isEqualTo("SERVICE_UNAVAILABLE");
    }

    // ─── Routes not in backend-aws ────────────────────────────────────────────

    @Test
    void searchRoute_notHandledByThisLambda_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE + "/search", "{}"), null);
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void retryRoute_notHandledByThisLambda_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE + "/retry", "{}"), null);
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void configRoute_notHandledByThisLambda_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", BASE + "/config", null), null);
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void findById_notHandledByThisLambda_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", BASE + "/123456", null), null);
        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void unknownRoute_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", "/completely/unknown", null), null);
        assertThat(response.getStatusCode()).isEqualTo(404);
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

    private static String postingBody() {
        return """
                {
                  "source_name": "IMX",
                  "source_reference_id": "SRC-001",
                  "end_to_end_reference_id": "E2E-001",
                  "request_type": "IMX_CBS_GL",
                  "credit_debit_indicator": "DEBIT",
                  "debtor_account": "1000123456",
                  "creditor_account": "1000654321",
                  "requested_execution_date": "2026-04-21",
                  "amount": { "value": "1000.00", "currency_code": "USD" }
                }
                """;
    }
}
