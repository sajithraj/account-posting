package com.accountposting.dto.retry;

import lombok.Data;

import java.util.List;

@Data
public class RetryRequestV2 {
    private List<Long> postingIds;
}
