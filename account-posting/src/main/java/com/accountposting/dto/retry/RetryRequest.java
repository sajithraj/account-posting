package com.accountposting.dto.retry;

import lombok.Data;

import java.util.List;

/**
 * Optional filter for retry. If postingIds is empty/null, all FAILED/PENDING legs
 * that are not currently locked will be retried.
 */
@Data
public class RetryRequest {
    private List<Long> postingIds;
}
