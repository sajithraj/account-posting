package com.sajith.payments.redesign.event;

import java.time.Instant;

/**
 * Published to Kafka when a posting reaches SUCCESS status (all legs succeeded).
 */
public record PostingSuccessEvent(
        Long postingId,
        String endToEndReferenceId,
        String requestType,
        String targetSystems,
        Instant eventTime
) {
}
