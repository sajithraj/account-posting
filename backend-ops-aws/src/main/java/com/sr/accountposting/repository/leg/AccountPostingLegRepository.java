package com.sr.accountposting.repository.leg;

import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
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
public class AccountPostingLegRepository {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingLegRepository.class);

    private final DynamoDbTable<AccountPostingLegEntity> table;

    @Inject
    public AccountPostingLegRepository() {
        DynamoDbEnhancedClient enhanced = AwsClientFactory.enhancedClient();
        this.table = enhanced.table(AppConfig.LEG_TABLE, TableSchema.fromBean(AccountPostingLegEntity.class));
        log.info("AccountPostingLegRepository initialized | table={}", AppConfig.LEG_TABLE);
    }

    public void update(AccountPostingLegEntity leg) {
        log.debug("Updating leg in DynamoDB | postingId={} transactionOrder={} transactionId={} status={}",
                leg.getPostingId(), leg.getTransactionOrder(), leg.getTransactionId(), leg.getStatus());
        table.updateItem(leg);
        log.debug("Leg updated in DynamoDB | postingId={} transactionOrder={}",
                leg.getPostingId(), leg.getTransactionOrder());
    }

    public List<AccountPostingLegEntity> findByPostingId(String postingId) {
        log.debug("Querying DynamoDB for legs | postingId={}", postingId);
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(postingId).build()
        );
        List<AccountPostingLegEntity> results = new ArrayList<>();
        table.query(condition).forEach(page -> results.addAll(page.items()));
        log.debug("DynamoDB leg query completed | postingId={} count={}", postingId, results.size());
        return results;
    }

    public Optional<AccountPostingLegEntity> findByPostingIdAndOrder(String postingId, Integer transactionOrder) {
        log.debug("Querying DynamoDB for leg by primary key | postingId={} transactionOrder={}",
                postingId, transactionOrder);
        AccountPostingLegEntity result = table.getItem(
                Key.builder().partitionValue(postingId).sortValue(transactionOrder).build()
        );
        if (result == null) {
            log.debug("Leg not found in DynamoDB | postingId={} transactionOrder={}", postingId, transactionOrder);
        } else {
            log.debug("Leg found in DynamoDB | postingId={} transactionOrder={} transactionId={} status={}",
                    postingId, transactionOrder, result.getTransactionId(), result.getStatus());
        }
        return Optional.ofNullable(result);
    }
}
