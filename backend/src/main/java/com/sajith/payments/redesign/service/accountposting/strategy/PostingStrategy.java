package com.sajith.payments.redesign.service.accountposting.strategy;


import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;

public interface PostingStrategy {

    /**
     * Unique key for this strategy: {@code <targetSystem>_<operation>}, e.g. {@code CBS_POSTING}.
     * Must match the concatenation of {@code target_system + "_" + operation}
     * in the {@code posting_config} table.
     */
    String getPostingFlow();

    /**
     * Submits to the target system and persists the transaction result.
     *
     * @param postingId       parent posting ID
     * @param transactionOrder order sequence from the config
     * @param request          original posting request
     * @param isRetry          when true, updates the existing transaction instead of inserting a new one
     * @param existingTxnId    must be non-null when isRetry=true; identifies the transaction to update
     */
    LegResponseV2 process(Long postingId, int transactionOrder, IncomingPostingRequest request,
                          boolean isRetry, Long existingTxnId);
}
