package com.accountposting.dto.retry;

import lombok.Data;

import java.util.List;

@Data
public class RetryRequest {
    private List<Long> postingIds;
}
