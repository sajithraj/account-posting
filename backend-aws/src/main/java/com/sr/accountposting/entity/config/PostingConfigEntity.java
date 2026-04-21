package com.sr.accountposting.entity.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@NoArgsConstructor
@DynamoDbBean
public class PostingConfigEntity {

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

    @DynamoDbPartitionKey
    public String getRequestType() {
        return requestType;
    }

    @DynamoDbSortKey
    public Integer getOrderSeq() {
        return orderSeq;
    }
}
