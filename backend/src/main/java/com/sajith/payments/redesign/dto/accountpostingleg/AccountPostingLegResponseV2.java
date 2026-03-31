package com.sajith.payments.redesign.dto.accountpostingleg;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegResponseV2 {

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("posting_id")
    private Long postingId;

    @JsonProperty("transaction_order")
    private Integer transactionOrder;

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
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant postedTime;

    @JsonProperty("mode")
    private LegMode mode;

    @JsonProperty("operation")
    private String operation;

    @JsonProperty("created_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant createdAt;

    @JsonProperty("updated_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant updatedAt;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("updated_by")
    private String updatedBy;
}
