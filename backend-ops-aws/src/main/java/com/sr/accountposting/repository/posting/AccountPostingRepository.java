package com.sr.accountposting.repository.posting;

import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.util.AppConfig;
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
import java.util.ArrayList;
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

    public void update(AccountPostingEntity posting) {
        table.updateItem(posting);
    }

    public List<AccountPostingEntity> search(String status, String sourceName,
                                             String fromDate, String toDate, int limit) {
        if (status != null) {
            return searchByStatusAndDateRange(status, fromDate, toDate, limit);
        }
        if (sourceName != null) {
            return searchBySourceAndDateRange(sourceName, fromDate, toDate, limit);
        }
        return scanWithLimit(limit);
    }

    private List<AccountPostingEntity> searchByStatusAndDateRange(String status,
                                                                  String fromDate, String toDate, int limit) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index("gsi-status-createdAt");
        QueryConditional condition = (fromDate != null && toDate != null)
                ? QueryConditional.sortBetween(
                Key.builder().partitionValue(status).sortValue(fromDate).build(),
                Key.builder().partitionValue(status).sortValue(toDate).build())
                : QueryConditional.keyEqualTo(Key.builder().partitionValue(status).build());

        List<AccountPostingEntity> results = new ArrayList<>();
        gsi.query(QueryEnhancedRequest.builder().queryConditional(condition).limit(limit).build())
                .stream().flatMap(page -> page.items().stream()).limit(limit).forEach(results::add);
        return results;
    }

    private List<AccountPostingEntity> searchBySourceAndDateRange(String sourceName,
                                                                  String fromDate, String toDate, int limit) {
        DynamoDbIndex<AccountPostingEntity> gsi = table.index("gsi-sourceName-createdAt");
        QueryConditional condition = (fromDate != null && toDate != null)
                ? QueryConditional.sortBetween(
                Key.builder().partitionValue(sourceName).sortValue(fromDate).build(),
                Key.builder().partitionValue(sourceName).sortValue(toDate).build())
                : QueryConditional.keyEqualTo(Key.builder().partitionValue(sourceName).build());

        List<AccountPostingEntity> results = new ArrayList<>();
        gsi.query(QueryEnhancedRequest.builder().queryConditional(condition).limit(limit).build())
                .stream().flatMap(page -> page.items().stream()).limit(limit).forEach(results::add);
        return results;
    }

    private List<AccountPostingEntity> scanWithLimit(int limit) {
        return table.scan().items().stream().limit(limit).collect(Collectors.toList());
    }
}
