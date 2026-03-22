package com.accountposting.dto.accountposting;

import com.accountposting.dto.accountpostingleg.LegCreateResponse;
import com.accountposting.entity.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountPostingCreateResponse {

    private String sourceReferenceId;
    private String endToEndReferenceId;
    private PostingStatus postingStatus;
    private Instant processedAt;
    private List<LegCreateResponse> responses;
}
