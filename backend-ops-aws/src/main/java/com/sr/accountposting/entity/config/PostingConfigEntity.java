package com.sr.accountposting.entity.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@NoArgsConstructor
@DynamoDbBean
public class PostingConfigEntity {

    private String configId;
    private String requestType;
    private Integer orderSeq;
    private String sourceName;
    private String targetSystem;
    private String operation;
    private String processingMode;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;

    @DynamoDbSecondaryPartitionKey(indexNames = {"gsi-configId"})
    @JsonProperty("config_id")
    public String getConfigId() {
        return configId;
    }

    @DynamoDbPartitionKey
    @JsonProperty("request_type")
    public String getRequestType() {
        return requestType;
    }

    @DynamoDbSortKey
    @JsonProperty("order_seq")
    public Integer getOrderSeq() {
        return orderSeq;
    }
}
