package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.BusinessException;
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

    @Test
    void createPosting_validRequest_returns200WithPostingResponse() throws Exception {
        PostingResponse posting = PostingResponse.builder()
                .endToEndReferenceId("E2E-001")
                .sourceReferenceId("SRC-001")
                .postingStatus("ACSP")
                .processedAt("2026-04-20T10:00:00")
                .build();
        when(postingService.create(any(IncomingPostingRequest.class))).thenReturn(posting);

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> body = MAPPER.readValue(response.getBody(), Map.class);
        assertThat(body.get("success")).isEqualTo(true);
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("end_to_end_reference_id")).isEqualTo("E2E-001");
        assertThat(data.get("posting_status")).isEqualTo("ACSP");
    }

    @Test
    void createPosting_businessException_returns422() {
        when(postingService.create(any())).thenThrow(new BusinessException("DUPLICATE", "Duplicate reference"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(422);
    }

    @Test
    void createPosting_validationException_returns400() {
        when(postingService.create(any())).thenThrow(new ValidationException("INVALID_FIELD", "Amount is required"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void createPosting_technicalException_returns500() {
        when(postingService.create(any())).thenThrow(new TechnicalException("Connection failed"));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE, postingBody()), null);

        assertThat(response.getStatusCode()).isEqualTo(500);
    }

    @Test
    void getConfig_allConfigs_returns200() {
        PostingConfigEntity config = new PostingConfigEntity();
        config.setRequestType("IMX_CBS_GL");
        when(configService.getAll()).thenReturn(List.of(config));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", BASE + "/config", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(configService).getAll();
    }

    @Test
    void getConfig_byRequestType_returns200() {
        when(configService.getByRequestType("IMX_CBS_GL")).thenReturn(List.of(new PostingConfigEntity()));

        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", BASE + "/config/IMX_CBS_GL", null), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(configService).getByRequestType("IMX_CBS_GL");
    }

    @Test
    void retryPosting_returns200WithRetryResponse() throws Exception {
        RetryResponse retryResponse = RetryResponse.builder()
                .totalPostings(3)
                .queued(2)
                .skippedLocked(1)
                .build();
        when(postingService.retry(any(RetryRequest.class))).thenReturn(retryResponse);

        String body = "{\"requestedBy\":\"test\"}";
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("POST", BASE + "/retry", body), null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        Map<?, ?> respBody = MAPPER.readValue(response.getBody(), Map.class);
        Map<?, ?> data = (Map<?, ?>) respBody.get("data");
        assertThat(data.get("total_postings")).isEqualTo(3);
        assertThat(data.get("queued")).isEqualTo(2);
        assertThat(data.get("skipped_locked")).isEqualTo(1);
    }

    @Test
    void unknownRoute_returns404() {
        APIGatewayV2HTTPResponse response = handler.handle(apiEvent("GET", "/unknown/path", null), null);

        assertThat(response.getStatusCode()).isEqualTo(404);
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
                  "requested_execution_date": "2026-04-20",
                  "amount": { "value": "1000.00", "currency_code": "USD" }
                }
                """;
    }
}
