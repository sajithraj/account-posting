package com.sr.accountposting.repository.posting;

import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.util.AppConfig;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class AccountPostingRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingRepository.class);

    private final DynamoDbTable<AccountPostingEntity> table;
    private final DynamoDbClient rawClient;
    private final String tableName;

    @Inject
    public AccountPostingRepository() {
        this.tableName = AppConfig.POSTING_TABLE;
        DynamoDbEnhancedClient enhanced = AwsClientFactory.enhancedClient();
        this.table = enhanced.table(tableName, TableSchema.fromBean(AccountPostingEntity.class));
        this.rawClient = AwsClientFactory.dynamoDbClient();
    }

    public void save(AccountPostingEntity posting) {
        table.putItem(posting);
    }

    public Optional<AccountPostingEntity> findById(String postingId) {
        AccountPostingEntity result = table.getItem(Key.builder().partitionValue(postingId).build());
        return Optional.ofNullable(result);
    }

    public Optional<AccountPostingEntity> findByEndToEndReferenceId(String e2eRef) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index("gsi-endToEndReferenceId");
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(e2eRef).build()
        );
        return gsi.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public Optional<AccountPostingEntity> findBySourceReferenceId(String sourceRefId) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index("gsi-sourceReferenceId");
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(sourceRefId).build()
        );
        return gsi.query(condition).stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public boolean existsByEndToEndReferenceId(String e2eRef) {
        return findByEndToEndReferenceId(e2eRef).isPresent();
    }

    public List<AccountPostingEntity> findByStatus(String status) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index("gsi-status-createdAt");
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status).build()
        );
        List<AccountPostingEntity> results = new ArrayList<>();
        gsi.query(condition).forEach(page -> results.addAll(page.items()));
        return results;
    }

    public boolean acquireRetryLock(String postingId, long lockUntilEpochMillis) {
        long now = System.currentTimeMillis();
        try {
            rawClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("postingId", AttributeValue.builder().s(postingId).build()))
                    .updateExpression("SET retryLockedUntil = :lockVal")
                    .conditionExpression(
                            "attribute_not_exists(retryLockedUntil) OR retryLockedUntil < :now")
                    .expressionAttributeValues(Map.of(
                            ":lockVal", AttributeValue.builder().n(String.valueOf(lockUntilEpochMillis)).build(),
                            ":now", AttributeValue.builder().n(String.valueOf(now)).build()
                    ))
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            log.info("Retry lock already held for postingId={}", postingId);
            return false;
        }
    }

    public void updateStatus(String postingId, String status) {
        rawClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("postingId", AttributeValue.builder().s(postingId).build()))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(
                        ":status", AttributeValue.builder().s(status).build()
                ))
                .build());
    }

    public void update(AccountPostingEntity posting) {
        table.updateItem(posting);
    }

    public SearchResult search(String status, String sourceName, String requestType,
                               String endToEndReferenceId, String sourceReferenceId,
                               String fromDate, String toDate, int limit, String pageToken) {
        PageToken token = decodePageToken(pageToken);
        int offset = token.getOffset() != null ? token.getOffset() : 0;
        if (status == null && sourceName == null && requestType == null
                && endToEndReferenceId == null && sourceReferenceId == null) {
            if (fromDate == null && token.getFromDate() != null) {
                fromDate = token.getFromDate();
            }
            if (toDate == null && token.getToDate() != null) {
                toDate = token.getToDate();
            }
        }
        List<AccountPostingEntity> candidates;

        if (endToEndReferenceId != null) {
            log.info("Executing DynamoDB Query using endToEndReferenceId index");
            candidates = findByEndToEndReferenceId(endToEndReferenceId)
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (sourceReferenceId != null) {
            log.info("Executing DynamoDB Query using sourceReferenceId index");
            candidates = findBySourceReferenceId(sourceReferenceId)
                    .map(List::of)
                    .orElseGet(List::of);
        } else if (requestType != null) {
            log.info("Executing DynamoDB Query using requestType index with date filters");
            candidates = searchByUpdatedAtIndex("gsi-requestType-updatedAt",
                    requestType, fromDate, toDate);
        } else if (status != null) {
            log.info("Executing DynamoDB Query using status index with date filters");
            candidates = searchByUpdatedAtIndex("gsi-status-updatedAt", status, fromDate, toDate);
        } else if (sourceName != null) {
            log.info("Executing DynamoDB Query using sourceName index with date filters");
            candidates = searchByUpdatedAtIndex("gsi-sourceName-updatedAt", sourceName, fromDate, toDate);
        } else if (fromDate != null || toDate != null) {
            log.info("Executing DynamoDB Query using entityType index with date filters");
            candidates = searchByUpdatedAtIndex("gsi-entityType-updatedAt", "POSTING", fromDate, toDate);
        } else {
            throw new ValidationException("SEARCH_REQUIRES_FILTER",
                    "At least one search criterion is required");
        }

        final String effectiveFromDate = fromDate;
        final String effectiveToDate = toDate;

        List<AccountPostingEntity> sortedResults = candidates.stream()
                .filter(p -> matches(p, status, sourceName, requestType, endToEndReferenceId,
                        sourceReferenceId, effectiveFromDate, effectiveToDate))
                .sorted(Comparator.comparing(AccountPostingEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        if (offset >= sortedResults.size()) {
            return new SearchResult(List.of(), null);
        }

        int endExclusive = Math.min(offset + limit, sortedResults.size());
        String nextPageToken = endExclusive < sortedResults.size()
                ? encodePageToken(new PageToken(endExclusive, effectiveFromDate, effectiveToDate))
                : null;
        return new SearchResult(sortedResults.subList(offset, endExclusive), nextPageToken);
    }

    private List<AccountPostingEntity> searchByUpdatedAtIndex(String indexName, String partitionValue,
                                                              String fromDate, String toDate) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index(indexName);
        QueryConditional condition = (fromDate != null && toDate != null)
                ? QueryConditional.sortBetween(
                Key.builder().partitionValue(partitionValue).sortValue(fromDate).build(),
                Key.builder().partitionValue(partitionValue).sortValue(toDate).build())
                : QueryConditional.keyEqualTo(Key.builder().partitionValue(partitionValue).build());
        List<AccountPostingEntity> results = new ArrayList<>();
        gsi.query(QueryEnhancedRequest.builder()
                        .queryConditional(condition)
                        .scanIndexForward(false)
                        .build())
                .forEach(page -> results.addAll(page.items()));
        return results;
    }

    private List<AccountPostingEntity> scanAll() {
        List<AccountPostingEntity> results = new ArrayList<>();
        table.scan().forEach(page -> results.addAll(page.items()));
        return results;
    }

    private boolean matches(AccountPostingEntity p, String status, String sourceName, String requestType,
                            String endToEndReferenceId, String sourceReferenceId,
                            String fromDate, String toDate) {
        return matchesValue(p.getStatus(), status)
                && matchesValue(p.getSourceName(), sourceName)
                && matchesValue(p.getRequestType(), requestType)
                && matchesValue(p.getEndToEndReferenceId(), endToEndReferenceId)
                && matchesValue(p.getSourceReferenceId(), sourceReferenceId)
                && matchesDateRange(p.getUpdatedAt(), fromDate, toDate);
    }

    private boolean matchesValue(String actual, String expected) {
        return expected == null || expected.equals(actual);
    }

    private boolean matchesDateRange(String updatedAt, String fromDate, String toDate) {
        if (updatedAt == null) {
            return fromDate == null && toDate == null;
        }
        if (fromDate != null && updatedAt.compareTo(fromDate) < 0) {
            return false;
        }
        return toDate == null || updatedAt.compareTo(toDate) <= 0;
    }

    private PageToken decodePageToken(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return new PageToken(0, null, null);
        }

        try {
            String json = new String(Base64.getUrlDecoder().decode(pageToken), StandardCharsets.UTF_8);
            PageToken token = JsonUtil.MAPPER.readValue(json, PageToken.class);
            if (token.getOffset() == null) {
                token.setOffset(0);
            }
            return token;
        } catch (Exception e) {
            throw new ValidationException("INVALID_PAGE_TOKEN", "page_token is invalid");
        }
    }

    private String encodePageToken(PageToken token) {
        try {
            String json = JsonUtil.MAPPER.writeValueAsString(token);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Page token serialization failed", e);
        }
    }

    public static class PageToken {
        private Integer offset;
        private String fromDate;
        private String toDate;

        public PageToken() {
        }

        public PageToken(Integer offset, String fromDate, String toDate) {
            this.offset = offset;
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        public Integer getOffset() {
            return offset;
        }

        public void setOffset(Integer offset) {
            this.offset = offset;
        }

        public String getFromDate() {
            return fromDate;
        }

        public void setFromDate(String fromDate) {
            this.fromDate = fromDate;
        }

        public String getToDate() {
            return toDate;
        }

        public void setToDate(String toDate) {
            this.toDate = toDate;
        }
    }

    public static class SearchResult {
        private final List<AccountPostingEntity> items;
        private final String nextPageToken;

        public SearchResult(List<AccountPostingEntity> items, String nextPageToken) {
            this.items = items;
            this.nextPageToken = nextPageToken;
        }

        public List<AccountPostingEntity> getItems() {
            return items;
        }

        public String getNextPageToken() {
            return nextPageToken;
        }
    }
}
