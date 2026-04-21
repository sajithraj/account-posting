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

    @JsonProperty("posting_id")
    private Long postingId;

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("credit_debit_indicator")
    private String creditDebitIndicator;

    @JsonProperty("debtor_account")
    private String debtorAccount;

    @JsonProperty("creditor_account")
    private String creditorAccount;

    @JsonProperty("requested_execution_date")
    private String requestedExecutionDate;

    @JsonProperty("remittance_information")
    private String remittanceInformation;

    @JsonProperty("posting_status")
    private String postingStatus;

    @JsonProperty("target_systems")
    private String targetSystems;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("processed_at")
    private String processedAt;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    @JsonProperty("legs")
    private List<LegResponse> legs;
}
