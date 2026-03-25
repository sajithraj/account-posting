package com.accountposting.service.accountposting.strategy;


import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;

public interface PostingStrategy {

    /**
     * Unique key for this strategy: {@code <targetSystem>_<operation>}, e.g. {@code CBS_POSTING}.
     * Must match the concatenation of {@code target_system + "_" + operation}
     * in the {@code posting_config} table.
     */
    String getPostingFlow();

    /**
     * Submits to the target system and persists the leg result.
     *
     * @param postingId     parent posting ID
     * @param legOrder      order sequence from the config (used to set legOrder on the leg)
     * @param request       original posting request
     * @param isRetry       when true, updates the existing leg instead of inserting a new one
     * @param existingLegId must be non-null when isRetry=true; identifies the leg to update
     */
    LegResponseV2 process(Long postingId, int legOrder, AccountPostingRequestV2 request,
                          boolean isRetry, Long existingLegId);
}
