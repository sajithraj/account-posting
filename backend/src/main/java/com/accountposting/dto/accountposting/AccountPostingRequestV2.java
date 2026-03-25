package com.accountposting.dto.accountposting;

import com.accountposting.entity.enums.CreditDebitIndicator;
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
    private String sourceReferenceId;

    @NotBlank
    private String endToEndReferenceId;

    @NotBlank
    private String sourceName;

    @NotBlank
    private String requestType;

    @NotNull
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO code")
    private String currency;

    @NotNull
    private CreditDebitIndicator creditDebitIndicator;

    @NotBlank
    private String debtorAccount;

    @NotBlank
    private String creditorAccount;

    @NotNull
    private LocalDate requestedExecutionDate;

    private String remittanceInformation;
}
