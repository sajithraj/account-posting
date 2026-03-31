package com.sajith.payments.redesign.dto.accountpostingleg;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.sajith.payments.redesign.entity.enums.LegMode;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegRequestV2 {

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

    @JsonProperty("posted_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
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
