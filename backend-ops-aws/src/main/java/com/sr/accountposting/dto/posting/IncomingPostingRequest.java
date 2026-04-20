package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingPostingRequest {

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("credit_debit_indicator")
    private String creditDebitIndicator;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("requested_execution_date")
    private String requestedExecutionDate;

    @JsonProperty("debtor_account")
    private String debtorAccount;

    @JsonProperty("creditor_account")
    private String creditorAccount;

    @JsonProperty("remittance_information")
    private String remittanceInformation;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("team_code")
    private String teamCode;

    @JsonProperty("transaction_code")
    private String transactionCode;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("cif")
    private String cif;

    @JsonProperty("customer_name")
    private String customerName;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Amount {
        @JsonProperty("value")
        private String value;

        @JsonProperty("currency_code")
        private String currencyCode;
    }
}
