package com.sr.accountposting.entity.leg;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@NoArgsConstructor
@DynamoDbBean
public class AccountPostingLegEntity {

    private String postingId;
    private Integer transactionOrder;
    private String targetSystem;
    private String account;
    private String status;
    private String referenceId;
    private String reason;
    private Integer attemptNumber;
    private String postedTime;
    private String requestPayload;
    private String responsePayload;
    private String mode;
    private String operation;
    private Long version;
    private String createdAt;
    private String updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long ttl;

    @DynamoDbPartitionKey
    public String getPostingId() {
        return postingId;
    }

    @DynamoDbSortKey
    public Integer getTransactionOrder() {
        return transactionOrder;
    }

    public Long getVersion() {
        return version;
    }
}
