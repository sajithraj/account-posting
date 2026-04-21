package com.sr.accountposting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for backend-aws (SQS consumer + POST create).
 * <p>
 * Run via:  mvn test -Plocalstack
 * Or right-click in IDE — the static block sets all required system properties.
 * <p>
 * Configs are seeded directly to DynamoDB since POST /config lives in backend-ops-aws.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalStackIntegrationTest {

    static {
        if (System.getenv("AWS_ACCESS_KEY_ID") == null) {
            System.setProperty("aws.accessKeyId", "test");
            System.setProperty("aws.secretAccessKey", "test");
            System.setProperty("aws.endpointUrl", "http://localhost:4566");
            System.setProperty("AWS_ACCOUNT_REGION", "ap-southeast-1");
            System.setProperty("POSTING_TABLE_NAME", "account-posting");
            System.setProperty("LEG_TABLE_NAME", "account-posting-leg");
            System.setProperty("CONFIG_TABLE_NAME", "account-posting-config");
            System.setProperty("PROCESSING_QUEUE_URL",
                    "http://localhost:4566/000000000000/posting-queue");
            System.setProperty("SUPPORT_ALERT_TOPIC_ARN",
                    "arn:aws:sns:ap-southeast-1:000000000000:posting-alerts");
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/v3/payment/account-posting";
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/posting-queue";

    private LambdaRequestHandler handler;
    private SqsClient sqsClient;
    private DynamoDbTable<PostingConfigEntity> configTable;

    private String asyncE2eRef;

    @BeforeAll
    void setup() {
        handler = new LambdaRequestHandler();

        String key = resolve("AWS_ACCESS_KEY_ID", "aws.accessKeyId", "test");
        String secret = resolve("AWS_SECRET_ACCESS_KEY", "aws.secretAccessKey", "test");
        String endpoint = resolve("AWS_ENDPOINT_URL", "aws.endpointUrl", "http://localhost:4566");
        String configTableName = resolve("CONFIG_TABLE_NAME", "CONFIG_TABLE_NAME", "account-posting-config");

        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(key, secret));

        sqsClient = SqsClient.builder()
                .region(Region.of("ap-southeast-1"))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(creds)
                .build();

        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder()
                        .region(Region.of("ap-southeast-1"))
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(creds)
                        .build())
                .build();

        configTable = enhancedClient.table(configTableName, TableSchema.fromBean(PostingConfigEntity.class));
        seedConfigs();
    }

    // ─── Scenario 1: Async posting (IMX_CBS_GL) ──────────────────────────────

    @Test
    @Order(1)
    void createPosting_async_returnsAcspStatus() throws Exception {
        asyncE2eRef = "E2E-ASYNC-" + System.currentTimeMillis();
        var r = invoke("POST", BASE, postingBody("IMX", asyncE2eRef, "IMX_CBS_GL"));

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        Map<?, ?> data = (Map<?, ?>) r.data;
        assertThat(data.get("posting_status")).isEqualTo("ACSP");
        assertThat(data.get("end_to_end_reference_id")).isEqualTo(asyncE2eRef);
        assertThat(data.get("processed_at")).isNotNull();
    }

    @Test
    @Order(2)
    void createPosting_duplicateE2eRef_returns422() throws Exception {
        // Uses the same asyncE2eRef created in order(1)
        var r = invoke("POST", BASE, postingBody("IMX", asyncE2eRef, "IMX_CBS_GL"));

        assertThat(r.status).isEqualTo(422);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(3)
    void processAsyncPosting_viaSqsEvent_processorRunsSuccessfully() throws Exception {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(3)
                .build()).messages();

        assertThat(messages).as("A PostingJob should be in the SQS queue after async create").isNotEmpty();

        Message msg = messages.get(0);
        Map<String, Object> record = new HashMap<>();
        record.put("eventSource", "aws:sqs");
        record.put("messageId", msg.messageId());
        record.put("body", msg.body());

        // SQS events return null (no HTTP response)
        Object result = handler.handleRequest(Map.of("Records", List.of(record)), null);
        assertThat(result).isNull();

        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(msg.receiptHandle())
                .build());
    }

    // ─── Scenario 2: Sync posting (ADD_ACCOUNT_HOLD) ─────────────────────────

    @Test
    @Order(4)
    void createPosting_sync_returnsImmediateResult() throws Exception {
        drainQueue();

        String syncE2eRef = "E2E-SYNC-" + System.currentTimeMillis();
        var r = invoke("POST", BASE, postingBody("STABLECOIN", syncE2eRef, "ADD_ACCOUNT_HOLD"));

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        Map<?, ?> data = (Map<?, ?>) r.data;
        assertThat(data.get("posting_status")).isIn("ACSP", "PNDG");
        assertThat(data.get("processed_at")).isNotNull();

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1)
                .build()).messages();
        assertThat(messages).as("Sync posting must not enqueue an SQS message").isEmpty();
    }

    // ─── Scenario 3: Validation errors ───────────────────────────────────────

    @Test
    @Order(5)
    void createPosting_unknownRequestType_returns400() throws Exception {
        var r = invoke("POST", BASE, postingBody("IMX", "E2E-UNKNOWN-" + System.currentTimeMillis(), "DOES_NOT_EXIST"));

        assertThat(r.status).isEqualTo(400);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(6)
    void createPosting_missingAmount_returns400() throws Exception {
        Map<String, Object> body = new HashMap<>(postingBody("IMX", "E2E-NO-AMT-" + System.currentTimeMillis(), "IMX_CBS_GL"));
        body.remove("amount");
        var r = invoke("POST", BASE, body);

        assertThat(r.status).isEqualTo(400);
        assertThat(r.success).isFalse();
    }

    // ─── Scenario 4: Routes not handled by backend-aws ───────────────────────

    @Test
    @Order(7)
    void searchRoute_notHandledByThisLambda_returns404() throws Exception {
        var r = invoke("POST", BASE + "/search", Map.of("status", "PNDG"));
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    @Order(8)
    void retryRoute_notHandledByThisLambda_returns404() throws Exception {
        var r = invoke("POST", BASE + "/retry", Map.of("requestedBy", "test"));
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    @Order(9)
    void configRoute_notHandledByThisLambda_returns404() throws Exception {
        var r = invoke("GET", BASE + "/config", null);
        assertThat(r.status).isEqualTo(404);
    }

    // ─── Seed helpers ─────────────────────────────────────────────────────────

    /**
     * Writes config rows directly to DynamoDB — POST /config is in backend-ops-aws.
     */
    private void seedConfigs() {
        upsertConfig("IMX_CBS_GL", 1, "IMX", "CBS", "POSTING", "ASYNC");
        upsertConfig("IMX_CBS_GL", 2, "IMX", "GL", "POSTING", "ASYNC");
        upsertConfig("ADD_ACCOUNT_HOLD", 1, "STABLECOIN", "CBS", "ADD_HOLD", "SYNC");
    }

    private void upsertConfig(String requestType, int orderSeq, String sourceName,
                              String targetSystem, String operation, String processingMode) {
        PostingConfigEntity config = new PostingConfigEntity();
        config.setRequestType(requestType);
        config.setOrderSeq(orderSeq);
        config.setSourceName(sourceName);
        config.setTargetSystem(targetSystem);
        config.setOperation(operation);
        config.setProcessingMode(processingMode);
        config.setCreatedAt("2026-04-21T00:00:00Z");
        config.setUpdatedAt("2026-04-21T00:00:00Z");
        configTable.putItem(config);
    }

    // ─── Invocation helpers ───────────────────────────────────────────────────

    private Map<String, Object> postingBody(String sourceName, String e2eRef, String requestType) {
        Map<String, Object> body = new HashMap<>();
        body.put("source_name", sourceName);
        body.put("source_reference_id", "SRC-" + System.currentTimeMillis());
        body.put("end_to_end_reference_id", e2eRef);
        body.put("request_type", requestType);
        body.put("credit_debit_indicator", "DEBIT");
        body.put("debtor_account", "1000123456");
        body.put("creditor_account", "1000654321");
        body.put("requested_execution_date", "2026-04-21");
        body.put("amount", Map.of("value", "1000.00", "currency_code", "USD"));
        return body;
    }

    @SuppressWarnings("unchecked")
    private ParsedResponse invoke(String method, String path, Object body) throws Exception {
        String bodyJson = body != null ? MAPPER.writeValueAsString(body) : null;
        System.out.printf("[TEST] --> %s %s  body=%s%n", method, path, bodyJson);

        Map<String, Object> http = new HashMap<>();
        http.put("method", method);
        http.put("path", path);
        Map<String, Object> requestContext = new HashMap<>();
        requestContext.put("http", http);
        Map<String, Object> event = new HashMap<>();
        event.put("requestContext", requestContext);
        event.put("rawPath", path);
        if (bodyJson != null) event.put("body", bodyJson);

        Object raw = handler.handleRequest(event, null);
        Map<String, Object> rawMap = (Map<String, Object>) raw;
        int statusCode = (int) rawMap.get("statusCode");
        String responseBody = (String) rawMap.get("body");
        System.out.printf("[TEST] <-- %d  body=%s%n", statusCode, responseBody);

        Map<String, Object> apiResponse = MAPPER.readValue(responseBody, Map.class);
        return new ParsedResponse(statusCode, (Boolean) apiResponse.get("success"), apiResponse.get("data"));
    }

    private void drainQueue() {
        while (true) {
            List<Message> msgs = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(0)
                    .build()).messages();
            if (msgs.isEmpty()) break;
            msgs.forEach(m -> sqsClient.deleteMessage(
                    software.amazon.awssdk.services.sqs.model.DeleteMessageRequest.builder()
                            .queueUrl(QUEUE_URL)
                            .receiptHandle(m.receiptHandle())
                            .build()));
        }
    }

    private static String resolve(String envKey, String propKey, String defaultVal) {
        String v = System.getenv(envKey);
        if (v != null && !v.isEmpty()) return v;
        v = System.getProperty(propKey);
        return v != null && !v.isEmpty() ? v : defaultVal;
    }

    record ParsedResponse(int status, boolean success, Object data) {
    }
}
