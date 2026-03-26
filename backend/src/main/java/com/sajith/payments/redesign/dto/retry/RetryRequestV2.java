package com.sajith.payments.redesign.dto.retry;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RetryRequestV2 {

    @JsonProperty("posting_ids")
    private List<Long> postingIds;
}
