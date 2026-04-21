package com.sr.accountposting;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.di.AppComponent;
import com.sr.accountposting.di.DaggerAppComponent;
import com.sr.accountposting.handler.ApiGatewayHandler;
import com.sr.accountposting.handler.SqsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LambdaRequestHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger log = LoggerFactory.getLogger(LambdaRequestHandler.class);

    private static final ObjectMapper EVENT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ApiGatewayHandler apiGatewayHandler;
    private final SqsHandler sqsHandler;

    public LambdaRequestHandler() {
        AppComponent component = DaggerAppComponent.create();
        this.apiGatewayHandler = component.apiGatewayHandler();
        this.sqsHandler = component.sqsHandler();
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        try {
            if (isSqsEvent(event)) {
                log.info("Event detected: SQS");
                sqsHandler.handle(event, context);
                return null;
            } else {
                log.info("Event detected: API Gateway");
                Map<String, Object> normalizedEvent = normalizeApiGatewayEvent(event);
                String json = EVENT_MAPPER.writeValueAsString(normalizedEvent);
                APIGatewayV2HTTPEvent apiEvent = EVENT_MAPPER.readValue(json, APIGatewayV2HTTPEvent.class);
                APIGatewayV2HTTPResponse response = apiGatewayHandler.handle(apiEvent, context);
                return EVENT_MAPPER.convertValue(response, Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize Lambda event", e);
            throw new RuntimeException("Event deserialization failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isSqsEvent(Map<String, Object> event) {
        Object records = event.get("Records");
        if (records instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> record) {
                return "aws:sqs".equals(record.get("eventSource"));
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeApiGatewayEvent(Map<String, Object> event) {
        Object requestContext = event.get("requestContext");
        if (requestContext instanceof Map<?, ?> requestContextMap
                && requestContextMap.get("http") instanceof Map<?, ?>) {
            return event;
        }

        if (!event.containsKey("httpMethod") || !event.containsKey("path")) {
            return event;
        }

        Map<String, Object> normalized = new HashMap<>();
        Map<String, Object> normalizedRequestContext = new HashMap<>();
        Map<String, Object> http = new HashMap<>();
        Map<String, Object> headers = event.get("headers") instanceof Map<?, ?> headerMap
                ? new HashMap<>((Map<String, Object>) headerMap)
                : new HashMap<>();

        String method = String.valueOf(event.get("httpMethod"));
        String path = String.valueOf(event.get("path"));
        String sourceIp = "";
        String userAgent = "";

        if (requestContext instanceof Map<?, ?> requestContextMap) {
            Object identity = requestContextMap.get("identity");
            if (identity instanceof Map<?, ?> identityMap) {
                sourceIp = stringValue(identityMap.get("sourceIp"));
                userAgent = stringValue(identityMap.get("userAgent"));
            }
        }

        http.put("method", method);
        http.put("path", path);
        http.put("sourceIp", sourceIp);
        http.put("userAgent", userAgent);

        normalizedRequestContext.put("http", http);
        normalized.put("version", "2.0");
        normalized.put("routeKey", "$default");
        normalized.put("rawPath", path);
        normalized.put("rawQueryString", "");
        normalized.put("headers", headers);
        normalized.put("queryStringParameters", event.get("queryStringParameters"));
        normalized.put("pathParameters", event.get("pathParameters"));
        normalized.put("body", event.get("body"));
        normalized.put("isBase64Encoded", Boolean.TRUE.equals(event.get("isBase64Encoded")));
        normalized.put("requestContext", normalizedRequestContext);
        return normalized;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
