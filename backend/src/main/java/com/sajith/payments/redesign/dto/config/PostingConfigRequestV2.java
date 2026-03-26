package com.sajith.payments.redesign.dto.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PostingConfigRequestV2 {

    @NotBlank(message = "sourceName is required")
    @JsonProperty("source_name")
    private String sourceName;

    @NotBlank(message = "requestType is required")
    @JsonProperty("request_type")
    private String requestType;

    @NotBlank(message = "targetSystem is required")
    @JsonProperty("target_system")
    private String targetSystem;

    @NotBlank(message = "operation is required")
    @JsonProperty("operation")
    private String operation;

    @NotNull(message = "orderSeq is required")
    @Positive(message = "orderSeq must be a positive integer")
    @JsonProperty("order_seq")
    private Integer orderSeq;
}
