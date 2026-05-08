package com.sr.accountposting.repository.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
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

    private static final Logger log = LoggerFactory.getLogger(PostingConfigRepository.class);

    private final DynamoDbTable<PostingConfigEntity> table;

    @Inject
    public PostingConfigRepository() {
        DynamoDbEnhancedClient enhanced = AwsClientFactory.enhancedClient();
        this.table = enhanced.table(AppConfig.CONFIG_TABLE, TableSchema.fromBean(PostingConfigEntity.class));
        log.info("PostingConfigRepository initialized | table={}", AppConfig.CONFIG_TABLE);
    }

    public void save(PostingConfigEntity config) {
        log.debug("Saving config to DynamoDB | configId={} requestType={} orderSeq={}",
                config.getConfigId(), config.getRequestType(), config.getOrderSeq());
        table.putItem(config);
        log.debug("Config saved to DynamoDB | configId={}", config.getConfigId());
    }

    public void delete(String requestType, Integer orderSeq) {
        log.debug("Deleting config from DynamoDB | requestType={} orderSeq={}", requestType, orderSeq);
        table.deleteItem(Key.builder()
                .partitionValue(requestType)
                .sortValue(orderSeq)
                .build());
        log.debug("Config deleted from DynamoDB | requestType={} orderSeq={}", requestType, orderSeq);
    }

    public Optional<PostingConfigEntity> findByRequestTypeAndOrderSeq(String requestType, Integer orderSeq) {
        log.debug("Querying DynamoDB for config | requestType={} orderSeq={}", requestType, orderSeq);
        PostingConfigEntity result = table.getItem(
                Key.builder().partitionValue(requestType).sortValue(orderSeq).build()
        );
        if (result == null) {
            log.debug("Config not found in DynamoDB | requestType={} orderSeq={}", requestType, orderSeq);
        } else {
            log.debug("Config found in DynamoDB | configId={} requestType={} orderSeq={}",
                    result.getConfigId(), requestType, orderSeq);
        }
        return Optional.ofNullable(result);
    }

    public List<PostingConfigEntity> findByRequestType(String requestType) {
        log.debug("Querying DynamoDB for configs by requestType | requestType={}", requestType);
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(requestType).build()
        );
        List<PostingConfigEntity> results = new ArrayList<>();
        table.query(condition).forEach(page -> results.addAll(page.items()));
        log.debug("DynamoDB config query completed | requestType={} count={}", requestType, results.size());
        return results;
    }

    public List<PostingConfigEntity> findAll() {
        log.debug("Scanning all configs from DynamoDB");
        List<PostingConfigEntity> results = new ArrayList<>();
        table.scan().items().forEach(results::add);
        log.debug("DynamoDB config scan completed | count={}", results.size());
        return results;
    }

    public boolean existsByRequestTypeAndOrderSeq(String requestType, Integer orderSeq) {
        return findByRequestTypeAndOrderSeq(requestType, orderSeq).isPresent();
    }
}
