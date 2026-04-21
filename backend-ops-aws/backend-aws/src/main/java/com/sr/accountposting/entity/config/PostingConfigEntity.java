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

    private String requestType;    // PK — e.g. "IMX_CBS_GL"
    private Integer orderSeq;       // SK — execution order: 1, 2, 3
    private String sourceName;
    private String targetSystem;   // "CBS", "GL", "OBPM"
    private String operation;      // "POSTING", "ADD_HOLD", "REMOVE_HOLD"
    private String processingMode; // "SYNC" or "ASYNC" — controls API vs SQS path
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
