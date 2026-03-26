package com.sajith.payments.redesign.dto.accountposting;

import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AccountPostingFullResponseV2 {

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
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("credit_debit_indicator")
    private String creditDebitIndicator;

    @JsonProperty("debtor_account")
    private String debtorAccount;

    @JsonProperty("creditor_account")
    private String creditorAccount;

    @JsonProperty("requested_execution_date")
    private LocalDate requestedExecutionDate;

    @JsonProperty("remittance_information")
    private String remittanceInformation;

    @JsonProperty("posting_status")
    private PostingStatus postingStatus;

    @JsonProperty("target_systems")
    private String targetSystems;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("processed_at")
    private Instant processedAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("responses")
    private List<LegResponseV2> responses;
}
