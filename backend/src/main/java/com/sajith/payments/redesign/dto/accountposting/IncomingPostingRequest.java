package com.sajith.payments.redesign.dto.accountposting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class IncomingPostingRequest {

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("source_reference_id")
    private String sourceRefId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndRefId;

    @JsonProperty("credit_debit_indicator")
    private String creditDebitIndicator;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("product_code")
    private String productCode;

    @JsonProperty("clearing_system")
    private String clearingSystem;

    @JsonProperty("requested_execution_date")
    private String requestedExecutionDate;

    @JsonProperty("debtor_account")
    private String debtorAccount;

    @JsonProperty("creditor_account")
    private String creditorAccount;

    @JsonProperty("remittance_information")
    private String remittanceInformation;

    @JsonProperty("direction")
    private String direction;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("team_code")
    private String teamCode;

    @JsonProperty("debit_rc")
    private String debitRc;

    @JsonProperty("credit_rc")
    private String creditRc;

    @JsonProperty("transaction_code")
    private String transactionCode;

    @JsonProperty("branch")
    private String branch;

    @JsonProperty("loan_type")
    private String loanType;

    @JsonProperty("line_desc")
    private String lineDesc;

    @JsonProperty("cif")
    private String cif;

    @JsonProperty("customer_name")
    private String customerName;

    @JsonProperty("hold_id")
    private String holdId;
}
