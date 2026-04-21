package com.sr.accountposting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.PostingStatus;
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
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for backend-ops-aws (dashboard operations).
 *
 * Run via:  mvn test -Plocalstack
 * Or right-click in IDE — the static block sets all required system properties.
 *
 * Seed strategy:
 *  - Configs : seeded via POST /config (ops endpoint)
 *  - Postings: written directly to DynamoDB (POST create lives in backend-aws)
 *  - Legs    : written directly to DynamoDB
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
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = "/v2/payment/account-posting";
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/posting-queue";

    // Seeded posting IDs — stable across test run
    private static final Long ACSP_POSTING_ID = 888_000_000_001L;
    private static final Long PNDG_POSTING_ID = 888_000_000_002L;

    private LambdaRequestHandler handler;
    private SqsClient sqsClient;

    @BeforeAll
    void setup() throws Exception {
        handler = new LambdaRequestHandler();

        String key = resolve("AWS_ACCESS_KEY_ID", "aws.accessKeyId", "test");
        String secret = resolve("AWS_SECRET_ACCESS_KEY", "aws.secretAccessKey", "test");
        String endpoint = resolve("AWS_ENDPOINT_URL", "aws.endpointUrl", "http://localhost:4566");
        String postingTable = resolve("POSTING_TABLE_NAME", "POSTING_TABLE_NAME", "account-posting");
        String legTable = resolve("LEG_TABLE_NAME", "LEG_TABLE_NAME", "account-posting-leg");

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

        // Seed configs via POST /config (ops endpoint)
        seedConfigs();

        // Seed postings + legs directly via DynamoDB
        DynamoDbTable<AccountPostingEntity> postingTbl =
                enhancedClient.table(postingTable, TableSchema.fromBean(AccountPostingEntity.class));
        DynamoDbTable<AccountPostingLegEntity> legTbl =
                enhancedClient.table(legTable, TableSchema.fromBean(AccountPostingLegEntity.class));

        seedPosting(postingTbl, ACSP_POSTING_ID, PostingStatus.ACSP, "E2E-ACSP-SEED-001", "IMX");
        seedPosting(postingTbl, PNDG_POSTING_ID, PostingStatus.PNDG, "E2E-PNDG-SEED-001", "RMS");

        seedLeg(legTbl, ACSP_POSTING_ID, 1, "CBS", "ACSP", "CBS-TXN-001");
        seedLeg(legTbl, ACSP_POSTING_ID, 2, "GL", "ACSP", "GL-TXN-001");
        seedLeg(legTbl, PNDG_POSTING_ID, 1, "OBPM", "FAILED", null);
    }

    // ─── Scenario 1: Config CRUD ──────────────────────────────────────────────

    @Test
    @Order(1)
    void getConfig_allRows_returnsNonEmptyList() throws Exception {
        var r = invoke("GET", BASE + "/config", null);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        assertThat((List<?>) r.data).isNotEmpty();
    }

    @Test
    @Order(2)
    void getConfig_byRequestType_returnsMatchingRows() throws Exception {
        var r = invoke("GET", BASE + "/config/IMX_CBS_GL", null);
        assertThat(r.status).isEqualTo(200);
        List<?> rows = (List<?>) r.data;
        assertThat(rows).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @Order(3)
    void createConfig_newType_returns201() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("request_type", "TEST_OPS_TYPE");
        body.put("order_seq", 1);
        body.put("source_name", "RMS");
        body.put("target_system", "GL");
        body.put("operation", "POSTING");
        body.put("processing_mode", "ASYNC");

        var r = invoke("POST", BASE + "/config", body);
        assertThat(r.status).isIn(201, 422); // 422 = already exists from previous run
    }

    @Test
    @Order(4)
    void createConfig_duplicate_returns422() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("request_type", "IMX_CBS_GL");
        body.put("order_seq", 1);
        body.put("source_name", "IMX");
        body.put("target_system", "CBS");
        body.put("operation", "POSTING");
        body.put("processing_mode", "ASYNC");

        var r = invoke("POST", BASE + "/config", body);
        assertThat(r.status).isEqualTo(422);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(5)
    void updateConfig_existingConfig_returns200WithUpdatedData() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("target_system", "CBS");
        body.put("operation", "POSTING_UPDATED");
        body.put("updated_by", "ops-test");

        var r = invoke("PUT", BASE + "/config/IMX_CBS_GL/1", body);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
    }

    @Test
    @Order(6)
    void updateConfig_notFound_returns404() throws Exception {
        var r = invoke("PUT", BASE + "/config/DOES_NOT_EXIST/99", Map.of("target_system", "CBS"));
        assertThat(r.status).isEqualTo(404);
        assertThat(r.success).isFalse();
    }

    @Test
    @Order(7)
    void deleteConfig_existingConfig_returns204() throws Exception {
        // Create a throwaway config first
        Map<String, Object> create = new HashMap<>();
        create.put("request_type", "TEMP_DELETE_TYPE");
        create.put("order_seq", 99);
        create.put("source_name", "IMX");
        create.put("target_system", "CBS");
        create.put("operation", "POSTING");
        create.put("processing_mode", "ASYNC");
        invoke("POST", BASE + "/config", create);

        var r = invoke("DELETE", BASE + "/config/TEMP_DELETE_TYPE/99", null);
        assertThat(r.status).isEqualTo(204);
    }

    @Test
    @Order(8)
    void deleteConfig_notFound_returns404() throws Exception {
        var r = invoke("DELETE", BASE + "/config/DOES_NOT_EXIST/0", null);
        assertThat(r.status).isEqualTo(404);
    }

    // ─── Scenario 2: Find by ID ───────────────────────────────────────────────

    @Test
    @Order(9)
    @SuppressWarnings("unchecked")
    void findById_existingPosting_returns200WithLegs() throws Exception {
        var r = invoke("GET", BASE + "/" + ACSP_POSTING_ID, null);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data.get("posting_status")).isEqualTo(PostingStatus.ACSP.name());
        assertThat(data.get("end_to_end_reference_id")).isEqualTo("E2E-ACSP-SEED-001");
        List<?> legs = (List<?>) data.get("legs");
        assertThat(legs).hasSize(2);
    }

    @Test
    @Order(10)
    void findById_nonExistentPosting_returns404() throws Exception {
        var r = invoke("GET", BASE + "/999999999999", null);
        assertThat(r.status).isEqualTo(404);
        assertThat(r.success).isFalse();
    }

    // ─── Scenario 3: Search ───────────────────────────────────────────────────

    @Test
    @Order(11)
    void search_byStatus_returnsMatchingPostings() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", PostingStatus.ACSP.name());
        body.put("limit", 10);

        var r = invoke("POST", BASE + "/search", body);
        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        List<?> results = (List<?>) r.data;
        assertThat(results).isNotEmpty();
    }

    @Test
    @Order(12)
    void search_bySourceName_returnsMatchingPostings() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("source_name", "IMX");
        body.put("limit", 10);

        var r = invoke("POST", BASE + "/search", body);
        assertThat(r.status).isEqualTo(200);
        List<?> results = (List<?>) r.data;
        assertThat(results).isNotEmpty();
    }

    @Test
    @Order(13)
    void search_withNoFilters_returnsPostings() throws Exception {
        var r = invoke("POST", BASE + "/search", Map.of("limit", 5));
        assertThat(r.status).isEqualTo(200);
    }

    // ─── Scenario 4: Retry ────────────────────────────────────────────────────

    @Test
    @Order(14)
    @SuppressWarnings("unchecked")
    void retry_allPending_queuesJobsToSqs() throws Exception {
        var r = invoke("POST", BASE + "/retry", Map.of("requested_by", "integration-test"));

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data).containsKey("total_postings");
        assertThat(data).containsKey("queued");
        assertThat(data).containsKey("skipped_locked");
        assertThat((Integer) data.get("total_postings")).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(15)
    @SuppressWarnings("unchecked")
    void retry_withSpecificPostingId_queuesOnlyThatPosting() throws Exception {
        // Clear any residual lock by using a fresh retry
        Map<String, Object> body = new HashMap<>();
        body.put("posting_ids", List.of(PNDG_POSTING_ID));
        body.put("requested_by", "integration-test");

        var r = invoke("POST", BASE + "/retry", body);
        assertThat(r.status).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data.get("total_postings")).isEqualTo(1);
        // queued=1 or skippedLocked=1 (if lock acquired in previous test) — both valid
        int queued = (int) data.get("queued");
        int skipped = (int) data.get("skipped_locked");
        assertThat(queued + skipped).isEqualTo(1);
    }

    // ─── Scenario 5: Legs ────────────────────────────────────────────────────

    @Test
    @Order(16)
    void listLegs_existingPosting_returnsAllLegs() throws Exception {
        var r = invoke("GET", BASE + "/" + ACSP_POSTING_ID + "/transaction", null);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        List<?> legs = (List<?>) r.data;
        assertThat(legs).hasSize(2);
    }

    @Test
    @Order(17)
    @SuppressWarnings("unchecked")
    void getLeg_specificOrder_returnsCorrectLeg() throws Exception {
        var r = invoke("GET", BASE + "/" + ACSP_POSTING_ID + "/transaction/1", null);

        assertThat(r.status).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data.get("target_system")).isEqualTo("CBS");
        assertThat(data.get("status")).isEqualTo("ACSP");
        assertThat(data.get("reference_id")).isEqualTo("CBS-TXN-001");
    }

    @Test
    @Order(18)
    void getLeg_notFound_returns404() throws Exception {
        var r = invoke("GET", BASE + "/" + ACSP_POSTING_ID + "/transaction/99", null);
        assertThat(r.status).isEqualTo(404);
    }

    @Test
    @Order(19)
    @SuppressWarnings("unchecked")
    void manualUpdateLeg_updatesStatusAndMode() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUCCESS");
        body.put("reason", "Manually resolved by ops team");
        body.put("requested_by", "ops-admin");

        var r = invoke("PATCH", BASE + "/" + PNDG_POSTING_ID + "/transaction/1", body);

        assertThat(r.status).isEqualTo(200);
        assertThat(r.success).isTrue();
        Map<String, Object> data = (Map<String, Object>) r.data;
        assertThat(data.get("status")).isEqualTo("SUCCESS");
        assertThat(data.get("mode")).isEqualTo("MANUAL");
        assertThat(data.get("reason")).isEqualTo("Manually resolved by ops team");
    }

    @Test
    @Order(20)
    void manualUpdateLeg_notFound_returns404() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "SUCCESS");
        body.put("reason", "test");
        body.put("requested_by", "admin");

        var r = invoke("PATCH", BASE + "/" + ACSP_POSTING_ID + "/transaction/99", body);
        assertThat(r.status).isEqualTo(404);
    }

    // ─── Scenario 6: Routes not in backend-ops-aws ───────────────────────────

    @Test
    @Order(21)
    void createPosting_notHandledByOpsLambda_returns404() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("source_name", "IMX");
        body.put("end_to_end_reference_id", "E2E-NOT-OPS");
        body.put("request_type", "IMX_CBS_GL");

        var r = invoke("POST", BASE, body);
        assertThat(r.status).isEqualTo(404);
    }

    // ─── Seed helpers ─────────────────────────────────────────────────────────

    private void seedConfigs() throws Exception {
        seedConfig("IMX_CBS_GL", 1, "IMX", "CBS", "POSTING", "ASYNC");
        seedConfig("IMX_CBS_GL", 2, "IMX", "GL", "POSTING", "ASYNC");
        seedConfig("ADD_ACCOUNT_HOLD", 1, "STABLECOIN", "CBS", "ADD_HOLD", "SYNC");
    }

    private void seedConfig(String requestType, int orderSeq, String sourceName,
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
                .as("Seed config %s orderSeq=%d", requestType, orderSeq)
                .isIn(201, 422); // 422 = already exists — acceptable
    }

    private void seedPosting(DynamoDbTable<AccountPostingEntity> table,
                              Long postingId, PostingStatus status, String e2eRef, String sourceName) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(postingId);
        p.setStatus(status.name());
        p.setEndToEndReferenceId(e2eRef);
        p.setSourceName(sourceName);
        p.setSourceReferenceId("SRC-" + postingId);
        p.setRequestType("IMX_CBS_GL");
        p.setAmount("1000.00");
        p.setCurrency("USD");
        p.setCreditDebitIndicator("DEBIT");
        p.setDebtorAccount("1000123456");
        p.setCreditorAccount("1000654321");
        p.setRequestedExecutionDate("2026-04-21");
        p.setCreatedAt("2026-04-21T00:00:00Z");
        p.setUpdatedAt("2026-04-21T00:00:00Z");
        p.setCreatedBy("SEED");
        p.setUpdatedBy("SEED");
        // requestPayload for retry support
        p.setRequestPayload(String.format(
                "{\"source_name\":\"%s\",\"source_reference_id\":\"SRC-%d\","
                        + "\"end_to_end_reference_id\":\"%s\",\"request_type\":\"IMX_CBS_GL\","
                        + "\"credit_debit_indicator\":\"DEBIT\",\"debtor_account\":\"1000123456\","
                        + "\"creditor_account\":\"1000654321\","
                        + "\"amount\":{\"value\":\"1000.00\",\"currency_code\":\"USD\"}}",
                sourceName, postingId, e2eRef));
        table.putItem(p);
    }

    private void seedLeg(DynamoDbTable<AccountPostingLegEntity> table,
                          Long postingId, int order, String targetSystem, String status, String refId) {
        AccountPostingLegEntity leg = new AccountPostingLegEntity();
        leg.setPostingId(postingId);
        leg.setTransactionOrder(order);
        leg.setTargetSystem(targetSystem);
        leg.setStatus(status);
        leg.setReferenceId(refId);
        leg.setOperation("POSTING");
        leg.setMode("NORM");
        leg.setAttemptNumber(1);
        leg.setCreatedAt("2026-04-21T00:00:00Z");
        leg.setUpdatedAt("2026-04-21T00:00:00Z");
        leg.setCreatedBy("SEED");
        leg.setUpdatedBy("SEED");
        table.putItem(leg);
    }

    // ─── Invocation helpers ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ParsedResponse invoke(String method, String path, Object body) throws Exception {
        String bodyJson = body != null ? MAPPER.writeValueAsString(body) : null;
        System.out.printf("[OPS-TEST] --> %s %s  body=%s%n", method, path, bodyJson);

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
        System.out.printf("[OPS-TEST] <-- %d  body=%s%n", statusCode, responseBody);

        Map<String, Object> apiResponse = MAPPER.readValue(responseBody, Map.class);
        return new ParsedResponse(statusCode, (Boolean) apiResponse.get("success"), apiResponse.get("data"));
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
