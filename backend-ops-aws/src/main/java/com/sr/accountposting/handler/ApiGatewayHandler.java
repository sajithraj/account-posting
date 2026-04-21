package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sr.accountposting.dto.posting.ManualUpdateRequest;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.response.ApiError;
import com.sr.accountposting.dto.response.ApiResponse;
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

/**
 * Handles all API Gateway V2 HTTP events for the operations/dashboard Lambda.
 *
 * <p>Routes supported:
 * <pre>
 *   POST   /v2/payment/account-posting/search              Search postings by status, source, date range
 *   POST   /v2/payment/account-posting/retry               Re-queue PNDG/RECEIVED postings to SQS
 *   GET    /v2/payment/account-posting/{id}                Fetch posting by ID (with legs)
 *   GET    /v2/payment/account-posting/{id}/transaction    List all legs for a posting
 *   GET    /v2/payment/account-posting/{id}/transaction/{order}  Get a single leg
 *   PATCH  /v2/payment/account-posting/{id}/transaction/{order}  Manual leg status override
 *   GET    /v2/payment/account-posting/config              List all routing configs
 *   GET    /v2/payment/account-posting/config/{requestType}      Configs by request type
 *   POST   /v2/payment/account-posting/config              Create a routing config entry
 *   PUT    /v2/payment/account-posting/config/{type}/{order}     Update a routing config entry
 *   DELETE /v2/payment/account-posting/config/{type}/{order}     Delete a routing config entry
 * </pre>
 *
 * <p>{@code POST /v2/payment/account-posting} (posting creation) is NOT handled here — it lives in
 * the {@code backend-aws} Lambda. Requests to that route return HTTP 404.
 *
 * <p>Error mapping:
 * <ul>
 *   <li>{@link com.sr.accountposting.exception.ResourceNotFoundException} → 404</li>
 *   <li>{@link com.sr.accountposting.exception.ValidationException} → 400</li>
 *   <li>{@link com.sr.accountposting.exception.BusinessException} → 422</li>
 *   <li>{@link com.sr.accountposting.exception.TechnicalException} → 500</li>
 * </ul>
 */
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
            response = error(404, "NOT_FOUND", e.getMessage());
        } catch (ValidationException e) {
            log.warn("VALIDATION_ERROR {} {}: {}", method, path, e.getMessage());
            response = error(400, e.getErrorCode(), e.getMessage());
        } catch (BusinessException e) {
            log.warn("BUSINESS_ERROR {} {} [{}]: {}", method, path, e.getErrorCode(), e.getMessage());
            response = error(422, e.getErrorCode(), e.getMessage());
        } catch (TechnicalException e) {
            log.error("TECHNICAL_ERROR {} {}: {}", method, path, e.getMessage(), e);
            response = error(500, "SERVICE_UNAVAILABLE", "A technical error occurred. Please try again or contact support.");
        } catch (Exception e) {
            log.error("UNHANDLED_ERROR {} {}", method, path, e);
            response = error(500, "INTERNAL_ERROR", "An unexpected error occurred");
        }

        log.info("<-- {} {} status={}", method, path, response.getStatusCode());
        return response;
    }

    private APIGatewayV2HTTPResponse route(String method, String path, APIGatewayV2HTTPEvent event) {
        String body = event.getBody();

        if ("POST".equals(method) && path.equals(BASE + "/search")) {
            PostingSearchRequest req = JsonUtil.fromJson(body, PostingSearchRequest.class);
            return ok(postingService.search(req));
        }
        if ("POST".equals(method) && path.equals(BASE + "/retry")) {
            RetryRequest req = JsonUtil.fromJson(body, RetryRequest.class);
            return ok(postingService.retry(req));
        }
        if ("GET".equals(method) && path.matches(BASE + "/\\d+")) {
            Long postingId = extractLastLongSegment(path);
            return ok(postingService.findById(postingId));
        }
        if ("GET".equals(method) && path.matches(BASE + "/\\d+/transaction")) {
            Long postingId = extractSegmentAt(path, -2);
            return ok(legService.listLegs(postingId));
        }
        if ("GET".equals(method) && path.matches(BASE + "/\\d+/transaction/\\d+")) {
            Long postingId = extractSegmentAt(path, -3);
            int transactionOrder = (int) extractLastLongSegment(path).longValue();
            return ok(legService.getLeg(postingId, transactionOrder));
        }
        if ("PATCH".equals(method) && path.matches(BASE + "/\\d+/transaction/\\d+")) {
            Long postingId = extractSegmentAt(path, -3);
            int transactionOrder = (int) extractLastLongSegment(path).longValue();
            ManualUpdateRequest req = JsonUtil.fromJson(body, ManualUpdateRequest.class);
            legService.manualUpdateLeg(postingId, transactionOrder,
                    req.getStatus(), req.getReason(), req.getRequestedBy());
            return ok(legService.getLeg(postingId, transactionOrder));
        }
        if ("GET".equals(method) && path.equals(BASE + "/config")) {
            return ok(configService.getAll());
        }
        if ("GET".equals(method) && path.matches(BASE + "/config/.+")) {
            String requestType = path.substring(path.lastIndexOf('/') + 1);
            return ok(configService.getByRequestType(requestType));
        }
        if ("POST".equals(method) && path.equals(BASE + "/config")) {
            PostingConfigEntity config = JsonUtil.fromJson(body, PostingConfigEntity.class);
            return created(configService.create(config));
        }
        if ("PUT".equals(method) && path.matches(BASE + "/config/.+/\\d+")) {
            String[] segments = path.split("/");
            String requestType = segments[segments.length - 2];
            Integer orderSeq = Integer.parseInt(segments[segments.length - 1]);
            PostingConfigEntity updated = JsonUtil.fromJson(body, PostingConfigEntity.class);
            return ok(configService.update(requestType, orderSeq, updated));
        }
        if ("DELETE".equals(method) && path.matches(BASE + "/config/.+/\\d+")) {
            String[] segments = path.split("/");
            String requestType = segments[segments.length - 2];
            Integer orderSeq = Integer.parseInt(segments[segments.length - 1]);
            configService.delete(requestType, orderSeq);
            return noContent();
        }

        log.warn("ROUTE_NOT_FOUND: no handler for {} {}", method, path);
        return error(404, "ROUTE_NOT_FOUND", "No handler for " + method + " " + path);
    }

    private APIGatewayV2HTTPResponse ok(Object data) {
        return response(200, JsonUtil.toJson(ApiResponse.ok(data)));
    }

    private APIGatewayV2HTTPResponse created(Object data) {
        return response(201, JsonUtil.toJson(ApiResponse.ok(data)));
    }

    private APIGatewayV2HTTPResponse noContent() {
        return response(204, "");
    }

    private APIGatewayV2HTTPResponse error(int statusCode, String code, String message) {
        ApiError err = ApiError.builder().name(code).message(message).build();
        return response(statusCode, JsonUtil.toJson(ApiResponse.fail(err)));
    }

    private APIGatewayV2HTTPResponse response(int statusCode, String body) {
        return APIGatewayV2HTTPResponse.builder()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(body)
                .build();
    }

    private Long extractLastLongSegment(String path) {
        String[] parts = path.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private Long extractSegmentAt(String path, int offset) {
        String[] parts = path.split("/");
        int idx = parts.length + offset;
        return Long.parseLong(parts[idx]);
    }
}
