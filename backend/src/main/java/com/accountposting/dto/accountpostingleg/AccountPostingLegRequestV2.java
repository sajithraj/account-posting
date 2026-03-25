package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegRequestV2 {

    @JsonProperty("leg_order")
    private Integer legOrder;

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

    @JsonProperty("posted_time")
    private Instant postedTime;

    @JsonProperty("request_payload")
    private String requestPayload;

    @JsonProperty("response_payload")
    private String responsePayload;

    @JsonProperty("mode")
    private LegMode mode;

    @JsonProperty("operation")
    private String operation;
}
