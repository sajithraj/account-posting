package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PostingSearchRequest {

    // Equality filters — all optional
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

    // Date range on createdAt
    @JsonProperty("from_date")
    private String fromDate;    // ISO-8601 e.g. "2024-01-01T00:00:00Z"

    @JsonProperty("to_date")
    private String toDate;

    // Cursor-based pagination
    @JsonProperty("limit")
    private Integer limit = 20;

    @JsonProperty("page_token")
    private String pageToken;   // base64-encoded DynamoDB LastEvaluatedKey; absent on first call
}
