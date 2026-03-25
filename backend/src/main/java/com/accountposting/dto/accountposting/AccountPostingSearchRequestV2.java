package com.accountposting.dto.accountposting;

import com.accountposting.entity.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AccountPostingSearchRequestV2 {

    @JsonProperty("status")
    private PostingStatus status;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("target_system")
    private String targetSystem;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonProperty("from_date")
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonProperty("to_date")
    private LocalDate toDate;
}
