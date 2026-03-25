package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegResponseV2 {

    @JsonProperty("posting_leg_id")
    private Long postingLegId;

    @JsonProperty("posting_id")
    private Long postingId;

    @JsonProperty("leg_order")
    private Integer legOrder;

    @JsonProperty("target_system")
    private String targetSystem;

    @JsonProperty("account")
    private String account;

    @JsonProperty("status")
    private LegStatus status;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("attempt_number")
    private Integer attemptNumber;

    @JsonProperty("posted_time")
    private Instant postedTime;

    @JsonProperty("mode")
    private LegMode mode;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
