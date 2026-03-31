package com.sajith.payments.redesign.dto.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PostingConfigResponseV2 {

    @JsonProperty("config_id")
    private Long configId;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("target_system")
    private String targetSystem;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("order_seq")
    private Integer orderSeq;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("updated_by")
    private String updatedBy;
}
