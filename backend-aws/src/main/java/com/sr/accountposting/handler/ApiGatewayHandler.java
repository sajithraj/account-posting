package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.response.ApiError;
import com.sr.accountposting.dto.response.ApiResponse;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.exception.TechnicalException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.service.posting.AccountPostingService;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class ApiGatewayHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiGatewayHandler.class);

    private static final String BASE = "/v2/payment/account-posting";

    private final AccountPostingService postingService;

    @Inject
    public ApiGatewayHandler(AccountPostingService postingService) {
        this.postingService = postingService;
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

    // ─── Router ───────────────────────────────────────────────────────────────

    private APIGatewayV2HTTPResponse route(String method, String path, APIGatewayV2HTTPEvent event) {
        String body = event.getBody();

        if ("POST".equals(method) && path.equals(BASE)) {
            IncomingPostingRequest req = JsonUtil.fromJson(body, IncomingPostingRequest.class);
            return ok(postingService.create(req));
        }

        log.warn("ROUTE_NOT_FOUND: no handler for {} {}", method, path);
        return error(404, "ROUTE_NOT_FOUND", "No handler for " + method + " " + path);
    }

    private APIGatewayV2HTTPResponse ok(Object data) {
        return response(200, JsonUtil.toJson(ApiResponse.ok(data)));
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
}
