package com.accountposting.dto.retry;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RetryResponseV2 {
    private int totalLegsRetried;
    private int successCount;
    private int failedCount;
    private List<LegRetryResult> results;

    @Data
    @Builder
    public static class LegRetryResult {
        private Long postingLegId;
        private Long postingId;
        private String previousStatus;
        private String newStatus;
        private String reason;
    }
}
