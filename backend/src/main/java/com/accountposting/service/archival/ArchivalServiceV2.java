package com.accountposting.service.archival;

/**
 * Moves aged {@code account_posting} and {@code account_posting_leg} rows to their
 * respective history tables and deletes the originals.
 *
 * <p>The scheduled entry point is {@link #archiveOldPostings()}.
 */
public interface ArchivalServiceV2 {

    /**
     * Triggered by the configured cron expression ({@code app.archival.cron}).
     * Processes records in batches until no more eligible rows remain.
     */
    void archiveOldPostings();
}
