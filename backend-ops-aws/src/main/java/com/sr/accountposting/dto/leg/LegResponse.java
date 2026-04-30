package com.sr.accountposting.dto.leg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegResponse {

    @JsonProperty("posting_id")
    private String postingId;

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("transaction_order")
    private Integer transactionOrder;

    @JsonProperty("target_system")
    private String targetSystem;

    @JsonProperty("account")
    private String account;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("attempt_number")
    private Integer attemptNumber;

    @JsonProperty("posted_time")
    private String postedTime;

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;
}
