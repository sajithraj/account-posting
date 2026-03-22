package com.accountposting.event;

import java.time.Instant;

/**
 * Published to Kafka when a posting reaches SUCCESS status (all legs succeeded).
 * Consumers receive this on the {@code account-posting-success} topic.
 */
public record PostingSuccessEvent(
        Long postingId,
        String endToEndReferenceId,
        String requestType,
        String targetSystems,
        Instant eventTime
) {
}
