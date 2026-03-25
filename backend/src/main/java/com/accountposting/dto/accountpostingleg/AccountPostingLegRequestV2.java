package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegRequestV2 {

    @NotNull(message = "legOrder is required")
    @Min(value = 1, message = "legOrder must be >= 1")
    private Integer legOrder;

    @NotBlank(message = "targetSystem is required")
    @Size(max = 100)
    private String targetSystem;

    @NotBlank(message = "account is required")
    @Size(max = 50)
    private String account;

    private String status;
    private String referenceId;
    private String reason;
    private Instant postedTime;
    private String requestPayload;
    private String responsePayload;
    private LegMode mode;
    private String operation;
}
