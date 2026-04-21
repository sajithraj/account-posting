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

import java.util.List;
import java.util.Map;

/**
 * AWS Lambda entry point for the posting-creation Lambda (account-posting-aws).
 *
 * <p>This handler is registered as the Lambda function handler in AWS. It accepts two event types:
 * <ul>
 *   <li><b>API Gateway V2 HTTP</b> — forwarded to {@link com.sr.accountposting.handler.ApiGatewayHandler}.
 *       Only {@code POST /v3/payment/account-posting} is handled here; all other routes return 404.</li>
 *   <li><b>SQS</b> — forwarded to {@link com.sr.accountposting.handler.SqsHandler}.
 *       The Lambda is triggered by the {@code PROCESSING_QUEUE_URL} queue. Each record in the batch is
 *       processed independently so a single failure does not poison the entire batch.</li>
 * </ul>
 *
 * <p>Dagger component is initialised once (static init) so it is reused across warm invocations.
 *
 * <p>Required environment variables:
 * <pre>
 *   POSTING_TABLE_NAME       DynamoDB table for account_posting rows
 *   LEG_TABLE_NAME           DynamoDB table for account_posting_leg rows
 *   CONFIG_TABLE_NAME        DynamoDB table for posting_config rows
 *   PROCESSING_QUEUE_URL     SQS queue URL — jobs published here for async/retry processing
 *   SUPPORT_ALERT_TOPIC_ARN  SNS topic ARN — failure alerts published by SqsHandler
 *   AWS_ACCOUNT_REGION       AWS region (e.g. ap-southeast-1)
 * </pre>
 */
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
                String json = EVENT_MAPPER.writeValueAsString(event);
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
}
