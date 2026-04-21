package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sr.accountposting.enums.RequestMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostingJob {

    @JsonProperty("posting_id")
    private String postingId;

    @JsonProperty("request_payload")
    private IncomingPostingRequest requestPayload;

    @JsonProperty("request_mode")
    private RequestMode requestMode;
}
