package com.sajith.payments.redesign.dto.accountposting;

import com.sajith.payments.redesign.dto.accountpostingleg.LegCreateResponseV2;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AccountPostingCreateResponseV2 {

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("posting_status")
    private PostingStatus postingStatus;

    @JsonProperty("processed_at")
    private Instant processedAt;

    @JsonProperty("responses")
    private List<LegCreateResponseV2> responses;
}
