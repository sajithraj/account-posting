package com.sajith.payments.redesign.dto.retry;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetryResponseV2 {

    @JsonProperty("total_postings")
    private int totalPostings;

    @JsonProperty("success_count")
    private int successCount;

    @JsonProperty("failed_count")
    private int failedCount;
}
