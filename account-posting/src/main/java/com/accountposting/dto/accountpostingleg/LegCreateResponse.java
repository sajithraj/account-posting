package com.accountposting.dto.accountpostingleg;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LegCreateResponse {

    private String name;
    private String type;
    private String account;
    private String referenceId;
    private Instant postedTime;
    private String status;
    private String reason;
}
