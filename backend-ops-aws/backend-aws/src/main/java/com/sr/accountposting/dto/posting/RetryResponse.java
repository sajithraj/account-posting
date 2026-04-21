package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RetryResponse {

    @JsonProperty("total_postings")
    private int totalPostings;

    @JsonProperty("queued")
    private int queued;

    @JsonProperty("skipped_locked")
    private int skippedLocked;

    @JsonProperty("message")
    private String message;
}
