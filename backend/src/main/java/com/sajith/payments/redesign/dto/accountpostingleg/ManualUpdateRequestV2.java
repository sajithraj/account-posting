package com.sajith.payments.redesign.dto.accountpostingleg;

import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ManualUpdateRequestV2 {

    @NotNull(message = "status is required")
    @JsonProperty("status")
    private LegStatus status;

    @JsonProperty("reason")
    private String reason;
}
