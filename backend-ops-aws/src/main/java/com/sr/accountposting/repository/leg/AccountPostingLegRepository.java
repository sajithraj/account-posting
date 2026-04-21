package com.sr.accountposting.repository.leg;

import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.util.AppConfig;
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
import java.util.stream.Collectors;

@Singleton
public class AccountPostingLegRepository {

    private final DynamoDbTable<AccountPostingLegEntity> table;

    @Inject
    public AccountPostingLegRepository() {
        DynamoDbEnhancedClient enhanced = AwsClientFactory.enhancedClient();
        this.table = enhanced.table(AppConfig.LEG_TABLE, TableSchema.fromBean(AccountPostingLegEntity.class));
    }

    public void update(AccountPostingLegEntity leg) {
        table.updateItem(leg);
    }

    public List<AccountPostingLegEntity> findByPostingId(String postingId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(postingId).build()
        );
        List<AccountPostingLegEntity> results = new ArrayList<>();
        table.query(condition).forEach(page -> results.addAll(page.items()));
        return results;
    }

    public Optional<AccountPostingLegEntity> findByPostingIdAndOrder(String postingId, Integer transactionOrder) {
        AccountPostingLegEntity result = table.getItem(
                Key.builder().partitionValue(postingId).sortValue(transactionOrder).build()
        );
        return Optional.ofNullable(result);
    }

    public List<AccountPostingLegEntity> findNonSuccessByPostingId(String postingId) {
        return findByPostingId(postingId).stream()
                .filter(leg -> !"SUCCESS".equals(leg.getStatus()))
                .collect(Collectors.toList());
    }
}
