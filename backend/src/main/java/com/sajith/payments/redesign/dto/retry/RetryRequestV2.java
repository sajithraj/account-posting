package com.sajith.payments.redesign.dto.retry;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class RetryRequestV2 {

    @JsonProperty("posting_ids")
    private List<Long> postingIds;

    @NotBlank(message = "requested_by is required")
    @JsonProperty("requested_by")
    private String requestedBy;
}
