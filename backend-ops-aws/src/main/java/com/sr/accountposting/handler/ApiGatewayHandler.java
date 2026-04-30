package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sr.accountposting.dto.posting.ManualUpdateRequest;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.exception.TechnicalException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.service.config.PostingConfigService;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.posting.AccountPostingService;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ApiGatewayHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayHandler.class);
    private static final String BASE = "/v3/payment/account-posting";

    private final AccountPostingService postingService;
    private final AccountPostingLegService legService;
    private final PostingConfigService configService;

    @Inject
    public ApiGatewayHandler(AccountPostingService postingService,
                             AccountPostingLegService legService,
                             PostingConfigService configService) {
        this.postingService = postingService;
        this.legService = legService;
        this.configService = configService;
    }

    public APIGatewayV2HTTPResponse handle(APIGatewayV2HTTPEvent event, Context context) {
        String method = event.getRequestContext().getHttp().getMethod().toUpperCase();
        String path = event.getRawPath();
        String body = event.getBody();
        log.info("--> {} {}  body={}", method, path, body);

        APIGatewayV2HTTPResponse response;
        try {
            response = route(method, path, event);
        } catch (ResourceNotFoundException e) {
            log.warn("NOT_FOUND {} {}: {}", method, path, e.getMessage());
            response = rawError(404, "NOT_FOUND", e.getMessage());
        } catch (ValidationException e) {
            log.warn("VALIDATION_ERROR {} {}: {}", method, path, e.getMessage());
            response = rawError(400, e.getErrorCode(), e.getMessage());
        } catch (BusinessException e) {
            log.warn("BUSINESS_ERROR {} {} [{}]: {}", method, path, e.getErrorCode(), e.getMessage());
            response = rawError(422, e.getErrorCode(), e.getMessage());
        } catch (TechnicalException e) {
            log.error("TECHNICAL_ERROR {} {}: {}", method, path, e.getMessage(), e);
            response = rawError(500, "SERVICE_UNAVAILABLE", "A technical error occurred. Please try again or contact support.");
        } catch (Exception e) {
            log.error("UNHANDLED_ERROR {} {}", method, path, e);
            response = rawError(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }

        log.info("<-- {} {} status={}", method, path, response.getStatusCode());
        return response;
    }

    private APIGatewayV2HTTPResponse route(String method, String path, APIGatewayV2HTTPEvent event) {
        String body = event.getBody();

        if (("POST".equals(method) || "GET".equals(method)) && path.equals(BASE + "/search")) {
            PostingSearchRequest req = "GET".equals(method)
                    ? new PostingSearchRequest()
                    : JsonUtil.fromJson(body != null && !body.isBlank() ? body : "{}", PostingSearchRequest.class);
            applySearchQueryParams(req, event.getQueryStringParameters());
            return rawJson(200, postingService.search(req));
        }
        if ("POST".equals(method) && path.equals(BASE + "/retry")) {
            RetryRequest req = JsonUtil.fromJson(body, RetryRequest.class);
            return rawJson(200, postingService.retry(req));
        }
        if ("GET".equals(method) && path.equals(BASE + "/config")) {
            return rawJson(200, configService.getAll());
        }
        if ("GET".equals(method) && path.matches(BASE + "/config/.+")) {
            String requestType = path.substring(path.lastIndexOf('/') + 1);
            return rawJson(200, configService.getByRequestType(requestType));
        }
        if ("POST".equals(method) && path.equals(BASE + "/config")) {
            PostingConfigEntity config = JsonUtil.fromJson(body, PostingConfigEntity.class);
            return rawJson(201, configService.create(config));
        }
        if ("PUT".equals(method) && path.equals(BASE + "/config")) {
            PostingConfigEntity updated = JsonUtil.fromJson(body, PostingConfigEntity.class);
            return rawJson(200, configService.update(updated.getConfigId(), updated));
        }
        if ("DELETE".equals(method) && path.equals(BASE + "/config")) {
            PostingConfigEntity req = JsonUtil.fromJson(body, PostingConfigEntity.class);
            configService.delete(req.getConfigId());
            return noContent();
        }
        if ("GET".equals(method) && path.matches(BASE + "/[^/]+")) {
            String postingId = extractPathSegment(path, -1);
            return rawJson(200, postingService.findById(postingId));
        }
        if ("GET".equals(method) && path.matches(BASE + "/[^/]+/transaction")) {
            String postingId = extractPathSegment(path, -2);
            return rawJson(200, legService.listLegs(postingId));
        }
        if ("GET".equals(method) && path.matches(BASE + "/[^/]+/transaction/[^/]+")) {
            String postingId = extractPathSegment(path, -3);
            String transactionId = extractPathSegment(path, -1);
            return rawJson(200, legService.getLeg(postingId, transactionId));
        }
        if ("PATCH".equals(method) && path.matches(BASE + "/[^/]+/transaction/[^/]+")) {
            String postingId = extractPathSegment(path, -3);
            String transactionId = extractPathSegment(path, -1);
            ManualUpdateRequest req = JsonUtil.fromJson(body, ManualUpdateRequest.class);
            legService.manualUpdateLeg(postingId, transactionId,
                    req.getStatus(), req.getReason(), req.getRequestedBy());
            return rawJson(200, legService.getLeg(postingId, transactionId));
        }

        log.warn("ROUTE_NOT_FOUND: no handler for {} {}", method, path);
        return rawError(404, "ROUTE_NOT_FOUND", "No handler for " + method + " " + path);
    }

    private APIGatewayV2HTTPResponse rawJson(int statusCode, Object data) {
        return response(statusCode, JsonUtil.toJson(data));
    }

    private APIGatewayV2HTTPResponse noContent() {
        return response(204, "");
    }

    private APIGatewayV2HTTPResponse rawError(int statusCode, String code, String message) {
        return response(statusCode, JsonUtil.toJson(Map.of(
                "id", UUID.randomUUID().toString(),
                "name", code,
                "message", message
        )));
    }

    private APIGatewayV2HTTPResponse response(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body)
                .build();
    }

    private String extractPathSegment(String path, int offsetFromEnd) {
        String[] parts = path.split("/");
        return parts[parts.length + offsetFromEnd];
    }

    private void applySearchQueryParams(PostingSearchRequest req, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return;
        }

        applyIfPresent(firstNonBlank(queryParams, "end_to_end_id", "end_to_end_reference_id"), req::setEndToEndReferenceId);
        applyIfPresent(firstNonBlank(queryParams, "source_ref_id", "source_reference_id"), req::setSourceReferenceId);
        applyIfPresent(firstNonBlank(queryParams, "source_name"), req::setSourceName);
        applyIfPresent(firstNonBlank(queryParams, "posting_status", "status"), req::setStatus);
        applyIfPresent(firstNonBlank(queryParams, "request_type"), req::setRequestType);
        applyIfPresent(firstNonBlank(queryParams, "from_date"), req::setFromDate);
        applyIfPresent(firstNonBlank(queryParams, "to_date"), req::setToDate);
        applyIfPresent(firstNonBlank(queryParams, "page_token"), req::setPageToken);

        String limit = firstNonBlank(queryParams, "limit");
        if (limit != null) {
            req.setLimit(Integer.parseInt(limit));
        }
    }

    private void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private String firstNonBlank(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

}
