package com.accountposting.dto.accountpostingleg;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class LegCreateResponseV2 {

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("account")
    private String account;

    @JsonProperty("reference_id")
    private String referenceId;

    @JsonProperty("posted_time")
    private Instant postedTime;

    @JsonProperty("status")
    private String status;

    @JsonProperty("reason")
    private String reason;
}
