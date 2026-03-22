package com.accountposting.dto.accountposting;

import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.entity.enums.PostingStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountPostingResponse {

    private Long postingId;
    private String sourceReferenceId;
    private String endToEndReferenceId;
    private String sourceName;
    private String requestType;
    private BigDecimal amount;
    private String currency;
    private String creditDebitIndicator;
    private String debtorAccount;
    private String creditorAccount;
    private LocalDate requestedExecutionDate;
    private String remittanceInformation;
    private PostingStatus postingStatus;
    private String targetSystems;
    private String reason;
    private Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<LegResponse> responses;
}
