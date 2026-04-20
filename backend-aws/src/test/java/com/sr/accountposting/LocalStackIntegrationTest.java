package com.sr.accountposting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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
 * End-to-end integration test against a running LocalStack instance.
 * <p>
 * Run via:  mvn test -Plocalstack            (Maven — sets env vars automatically)
 * Right-click → Run in IntelliJ    (static block sets system properties)
 * <p>
 * Required env vars (set by the Maven localstack profile or the static block below):
 * AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_ENDPOINT_URL,
 * AWS_ACCOUNT_REGION, POSTING_TABLE_NAME, LEG_TABLE_NAME,
 * CONFIG_TABLE_NAME, PROCESSING_QUEUE_URL, SUPPORT_ALERT_TOPIC_ARN
 *
 * @BeforeAll seeds the config table so every test can run independently.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalStackIntegrationTest {

    static {
        if (System.getenv("AWS_ACCESS_KEY_ID") == null) {
            System.setProperty("aws.accessKeyId", "test");
            System.setProperty("aws.secretAccessKey", "test");
            System.setProperty("aws.endpointUrl", "http://localhost:4566");
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
    private static final String BASE = "/v2/payment/account-posting";
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/posting-queue";

    private LambdaRequestHandler handler;
    private SqsClient sqsClient;

    private String asyncE2eRef;

    @BeforeAll
    void setup() throws Exception {
        handler = new LambdaRequestHandler();

        String key = coalesce(System.getenv("AWS_ACCESS_KEY_ID"), System.getProperty("aws.accessKeyId"), "test");
        String secret = coalesce(System.getenv("AWS_SECRET_ACCESS_KEY"), System.getProperty("aws.secretAccessKey"), "test");
        String url = coalesce(System.getenv("AWS_ENDPOINT_URL"), System.getProperty("aws.endpointUrl"), "http://localhost:4566");

        sqsClient = SqsClient.builder()
                .region(Region.of("ap-southeast-1"))
                .endpointOverride(URI.create(url))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(key, secret)))
                .build();

        seedConfigs();
    }

    /**
     * Upserts all configs required by every test so tests can run independently.
     */
    private void seedConfigs() throws Exception {
        seedRow("IMX_CBS_GL", 1, "IMX", "CBS", "POSTING", "ASYNC");
        seedRow("IMX_CBS_GL", 2, "IMX", "GL", "POSTING", "ASYNC");
        seedRow("ADD_ACCOUNT_HOLD", 1, "STABLECOIN", "CBS", "ADD_HOLD", "SYNC");
    }

    // ─── Scenario 1: Config read ──────────────────────────────────────────────

    @Test
    @Order(1)
    void getConfig_allRows() throws Exception {
        var r = invoke("GET", BASE + "/config", null);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        assertThat((List<?>) r.data).isNotEmpty();
    }

    @Test
    @Order(2)
    void getConfig_byRequestType() throws Exception {
        var r = invoke("GET", BASE + "/config/IMX_CBS_GL", null);
        assertThat(r.status).isEqualTo(200);
        List<?> rows = (List<?>) r.data;
        assertThat(rows).hasSize(2);
    }

    // ─── Scenario 2: Async posting (IMX_CBS_GL) ──────────────────────────────

    @Test
    @Order(3)
    void createPosting_async_returnedAccepted() throws Exception {
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
    @Order(4)
    void createPosting_duplicateE2eRef_returns422() throws Exception {
        var r = invoke("POST", BASE, postingBody("IMX", asyncE2eRef, "IMX_CBS_GL"));
        assertThat(r.status).isEqualTo(422);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void processAsyncPosting_viaSqsEvent() throws Exception {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(3)
                .build()).messages();

        assertThat(messages).as("Expected a PostingJob in the SQS queue").isNotEmpty();

        Message msg = messages.get(0);

        Map<String, Object> record = new HashMap<>();
        record.put("eventSource", "aws:sqs");
        record.put("messageId", msg.messageId());
        record.put("body", msg.body());

        Object result = handler.handleRequest(Map.of("Records", List.of(record)), null);
        assertThat(result).isNull();

        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(QUEUE_URL)
                .receiptHandle(msg.receiptHandle())
                .build());
    }

    // ─── Scenario 3: Sync posting (ADD_ACCOUNT_HOLD) ─────────────────────────

    @Test
    @Order(6)
    void createPosting_sync_returnsImmediateResult() throws Exception {
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

    // ─── Scenario 4: Search ───────────────────────────────────────────────────

    @Test
    @Order(7)
    void search_byStatus() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "RECEIVED");
        body.put("limit", 10);
        var r = invoke("POST", BASE + "/search", body);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
    }

    @Test
    @Order(8)
    void search_bySourceName() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceName", "IMX");
        body.put("limit", 10);
        var r = invoke("POST", BASE + "/search", body);
        assertThat(r.status).isEqualTo(200);
        List<?> results = (List<?>) r.data;
        assertThat(results).isNotEmpty();
    }

    // ─── Scenario 5: Validation errors ───────────────────────────────────────

    @Test
    @Order(9)
    void createPosting_unknownRequestType_returns400() throws Exception {
        var r = invoke("POST", BASE, postingBody("IMX", "E2E-UNKNOWN-001", "DOES_NOT_EXIST"));
        assertThat(r.status).isEqualTo(400);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(10)
    void createPosting_missingAmount_returns400() throws Exception {
        Map<String, Object> body = new HashMap<>(postingBody("IMX", "E2E-NO-AMOUNT-001", "IMX_CBS_GL"));
        body.remove("amount");
        var r = invoke("POST", BASE, body);
        assertThat(r.status).isEqualTo(400);
    }

    // ─── Scenario 6: Retry ───────────────────────────────────────────────────

    @Test
    @Order(11)
    @SuppressWarnings("unchecked")
    void retry_queuesAllPendingPostings() throws Exception {
        var r = invoke("POST", BASE + "/retry", Map.of("requestedBy", "integration-test"));
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();

        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data).containsKey("total_postings");
        assertThat(data).containsKey("queued");
        assertThat(data).containsKey("skipped_locked");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void seedRow(String requestType, int orderSeq, String sourceName,
                         String targetSystem, String operation, String processingMode) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("request_type", requestType);
        body.put("order_seq", orderSeq);
        body.put("source_name", sourceName);
        body.put("target_system", targetSystem);
        body.put("operation", operation);
        body.put("processing_mode", processingMode);
        var r = invoke("POST", BASE + "/config", body);
        assertThat(r.status)
                .as("Seed config %s orderSeq=%d — response body printed above", requestType, orderSeq)
                .isIn(200, 201, 422); // 422 = already exists, data is present — acceptable
    }

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

    private static String coalesce(String... values) {
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return null;
    }

    record ParsedResponse(int status, boolean success, Object data) {
    }
}
