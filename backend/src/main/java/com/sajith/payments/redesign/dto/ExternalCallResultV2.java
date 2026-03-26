package com.sajith.payments.redesign.dto;

import com.sajith.payments.redesign.dto.accountpostingleg.UpdateLegRequestV2;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;

/**
 * Holds the parsed outcome of an external system call (CBS / GL / OBPM).
 * Used as the MapStruct source when building an {@link UpdateLegRequestV2}.
 */
public record ExternalCallResultV2(
        LegStatus status,
        String referenceId,
        String reason,
        String requestPayload,
        String responsePayload,
        LegMode mode
) {
}
