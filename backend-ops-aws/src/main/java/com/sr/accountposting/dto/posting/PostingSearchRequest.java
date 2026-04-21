package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PostingSearchRequest {

    @JsonProperty("status")
    private String status;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("from_date")
    private String fromDate;

    @JsonProperty("to_date")
    private String toDate;

    @JsonProperty("limit")
    private Integer limit = 20;

    @JsonProperty("page_token")
    private String pageToken;
}
