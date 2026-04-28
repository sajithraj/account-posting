package com.sr.accountposting.entity.posting;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@Data
@NoArgsConstructor
@DynamoDbBean
public class AccountPostingEntity {

    private String postingId;
    private String sourceReferenceId;
    private String endToEndReferenceId;
    private String sourceName;
    private String requestType;
    private String amount;
    private String currency;
    private String creditDebitIndicator;
    private String debtorAccount;
    private String creditorAccount;
    private String requestedExecutionDate;
    private String remittanceInformation;
    private String status;
    private String targetSystems;
    private String reason;
    private String requestPayload;
    private String responsePayload;
    private Long retryLockedUntil;
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

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-endToEndReferenceId")
    public String getEndToEndReferenceId() {
        return endToEndReferenceId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"gsi-status-createdAt", "gsi-status-updatedAt"})
    public String getStatus() {
        return status;
    }

    @DynamoDbSecondarySortKey(indexNames = {"gsi-status-createdAt", "gsi-sourceName-createdAt"})
    public String getCreatedAt() {
        return createdAt;
    }

    @DynamoDbSecondarySortKey(indexNames = {
            "gsi-status-updatedAt",
            "gsi-sourceName-updatedAt",
            "gsi-requestType-updatedAt",
            "gsi-sourceReferenceId-updatedAt"
    })
    public String getUpdatedAt() {
        return updatedAt;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = {"gsi-sourceName-createdAt", "gsi-sourceName-updatedAt"})
    public String getSourceName() {
        return sourceName;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-requestType-updatedAt")
    public String getRequestType() {
        return requestType;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "gsi-sourceReferenceId-updatedAt")
    public String getSourceReferenceId() {
        return sourceReferenceId;
    }

    public Long getVersion() {
        return version;
    }
}
