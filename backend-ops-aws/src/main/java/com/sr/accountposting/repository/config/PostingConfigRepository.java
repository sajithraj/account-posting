package com.sr.accountposting.repository.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.util.AppConfig;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
public class PostingConfigRepository {

    private final DynamoDbTable<PostingConfigEntity> table;
    private final DynamoDbIndex<PostingConfigEntity> configIdIndex;

    @Inject
    public PostingConfigRepository() {
        DynamoDbEnhancedClient enhanced = AwsClientFactory.enhancedClient();
        this.table = enhanced.table(AppConfig.CONFIG_TABLE, TableSchema.fromBean(PostingConfigEntity.class));
        this.configIdIndex = table.index("gsi-configId");
    }

    public void save(PostingConfigEntity config) {
        table.putItem(config);
    }

    public void delete(String requestType, Integer orderSeq) {
        table.deleteItem(Key.builder()
                .partitionValue(requestType)
                .sortValue(orderSeq)
                .build());
    }

    public void deleteByConfigId(String configId) {
        findByConfigId(configId).ifPresent(entity ->
                table.deleteItem(Key.builder()
                        .partitionValue(entity.getRequestType())
                        .sortValue(entity.getOrderSeq())
                        .build())
        );
    }

    public Optional<PostingConfigEntity> findByConfigId(String configId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(configId).build()
        );
        List<PostingConfigEntity> results = new ArrayList<>();
        configIdIndex.query(condition).forEach(page -> results.addAll(page.items()));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<PostingConfigEntity> findByRequestTypeAndOrderSeq(String requestType, Integer orderSeq) {
        PostingConfigEntity result = table.getItem(
                Key.builder().partitionValue(requestType).sortValue(orderSeq).build()
        );
        return Optional.ofNullable(result);
    }

    public List<PostingConfigEntity> findByRequestType(String requestType) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(requestType).build()
        );
        List<PostingConfigEntity> results = new ArrayList<>();
        table.query(condition).forEach(page -> results.addAll(page.items()));
        return results;
    }

    public List<PostingConfigEntity> findAll() {
        List<PostingConfigEntity> results = new ArrayList<>();
        table.scan().items().forEach(results::add);
        return results;
    }

    public boolean existsByRequestTypeAndOrderSeq(String requestType, Integer orderSeq) {
        return findByRequestTypeAndOrderSeq(requestType, orderSeq).isPresent();
    }
}
