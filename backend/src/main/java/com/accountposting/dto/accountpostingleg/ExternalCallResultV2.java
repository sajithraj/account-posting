package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;

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
