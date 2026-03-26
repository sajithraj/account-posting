package com.sajith.payments.redesign.dto.accountpostingleg;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import lombok.Data;

import java.time.Instant;

@Data
public class LegResponseV2 {

    @JsonProperty("posting_leg_id")
    private Long postingLegId;

    @JsonProperty("leg_order")
    private Integer legOrder;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("account")
    private String account;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("posted_time")
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant postedTime;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("mode")
    private String mode;
}
