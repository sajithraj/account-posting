package com.accountposting.dto.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PostingConfigRequest {

    @NotBlank(message = "sourceName is required")
    private String sourceName;

    @NotBlank(message = "requestType is required")
    private String requestType;

    @NotBlank(message = "targetSystem is required")
    private String targetSystem;

    @NotBlank(message = "operation is required")
    private String operation;

    @NotNull(message = "orderSeq is required")
    @Positive(message = "orderSeq must be a positive integer")
    private Integer orderSeq;
}
