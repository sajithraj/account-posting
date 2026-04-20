package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RetryRequest {

    @JsonProperty("posting_ids")
    private List<Long> postingIds;

    @JsonProperty("requested_by")
    private String requestedBy;
}
