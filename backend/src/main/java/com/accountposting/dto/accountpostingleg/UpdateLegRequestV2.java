package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class UpdateLegRequestV2 {

    @NotNull(message = "status is required")
    private LegStatus status;

    private String referenceId;
    private String reason;
    private Instant postedTime;
    private String requestPayload;
    private String responsePayload;
    private LegMode mode;
}
