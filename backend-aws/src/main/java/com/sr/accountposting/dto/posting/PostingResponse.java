package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sr.accountposting.dto.leg.LegResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostingResponse {

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("posting_status")
    private String postingStatus;

    @JsonProperty("processed_at")
    private String processedAt;

    @JsonProperty("legs")
    private List<LegResponse> legs;
}
