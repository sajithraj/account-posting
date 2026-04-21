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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * AWS Lambda entry point for the operations/dashboard Lambda (account-posting-ops-aws).
 *
 * <p>This handler accepts <b>only API Gateway V2 HTTP events</b> — there is no SQS consumer here.
 * All events are routed to {@link com.sr.accountposting.handler.ApiGatewayHandler}.
 *
 * <p>Routes handled:
 * <pre>
 *   POST   /v2/payment/account-posting/search              Search postings (DynamoDB scan)
 *   POST   /v2/payment/account-posting/retry               Re-queue PNDG/RECEIVED postings to SQS
 *   GET    /v2/payment/account-posting/{id}                Fetch posting by ID with legs
 *   GET    /v2/payment/account-posting/{id}/transaction    List all legs for a posting
 *   GET    /v2/payment/account-posting/{id}/transaction/{order}   Get a single leg
 *   PATCH  /v2/payment/account-posting/{id}/transaction/{order}   Manually update a leg
 *   GET    /v2/payment/account-posting/config              List all routing configs
 *   GET    /v2/payment/account-posting/config/{requestType}       Configs by request type
 *   POST   /v2/payment/account-posting/config              Create routing config
 *   PUT    /v2/payment/account-posting/config/{type}/{order}      Update routing config
 *   DELETE /v2/payment/account-posting/config/{type}/{order}      Delete routing config
 * </pre>
 *
 * <p>Dagger component is initialised once and reused across warm invocations.
 *
 * <p>Required environment variables:
 * <pre>
 *   POSTING_TABLE_NAME    DynamoDB table for account_posting rows
 *   LEG_TABLE_NAME        DynamoDB table for account_posting_leg rows
 *   CONFIG_TABLE_NAME     DynamoDB table for posting_config rows
 *   PROCESSING_QUEUE_URL  SQS queue URL — retry jobs published here
 *   AWS_ACCOUNT_REGION    AWS region (e.g. ap-southeast-1)
 * </pre>
 */
public class LambdaRequestHandler implements RequestHandler<Map<String, Object>, Object> {

    private static final Logger log = LoggerFactory.getLogger(LambdaRequestHandler.class);

    private static final ObjectMapper EVENT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ApiGatewayHandler apiGatewayHandler;

    public LambdaRequestHandler() {
        AppComponent component = DaggerAppComponent.create();
        this.apiGatewayHandler = component.apiGatewayHandler();
    }

    @Override
    public Object handleRequest(Map<String, Object> event, Context context) {
        try {
            log.info("Event detected: API Gateway");
            String json = EVENT_MAPPER.writeValueAsString(event);
            APIGatewayV2HTTPEvent apiEvent = EVENT_MAPPER.readValue(json, APIGatewayV2HTTPEvent.class);
            APIGatewayV2HTTPResponse response = apiGatewayHandler.handle(apiEvent, context);
            return EVENT_MAPPER.convertValue(response, Map.class);
        } catch (Exception e) {
            log.error("Failed to deserialize Lambda event", e);
            throw new RuntimeException("Event deserialization failed", e);
        }
    }
}
