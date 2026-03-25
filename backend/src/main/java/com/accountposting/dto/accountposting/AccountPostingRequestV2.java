package com.accountposting.dto.accountposting;

import com.accountposting.entity.enums.CreditDebitIndicator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AccountPostingRequestV2 {

    @NotBlank
    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @NotBlank
    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @NotBlank
    @JsonProperty("source_name")
    private String sourceName;

    @NotBlank
    @JsonProperty("request_type")
    private String requestType;

    @NotNull
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    @JsonProperty("currency")
    private String currency;

    @NotNull
    @JsonProperty("credit_debit_indicator")
    private CreditDebitIndicator creditDebitIndicator;

    @NotBlank
    @JsonProperty("debtor_account")
    private String debtorAccount;

    @NotBlank
    @JsonProperty("creditor_account")
    private String creditorAccount;

    @NotNull
    @JsonProperty("requested_execution_date")
    private LocalDate requestedExecutionDate;

    @JsonProperty("remittance_information")
    private String remittanceInformation;
}
