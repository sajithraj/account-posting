package com.accountposting.service;

import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.UpdateLegRequest;
import com.accountposting.entity.enums.LegStatus;

import java.util.List;

public interface AccountPostingLegService {

    com.accountposting.dto.accountpostingleg.AccountPostingLegResponse addLeg(Long postingId, com.accountposting.dto.accountpostingleg.AccountPostingLegRequest request);

    List<AccountPostingLegResponse> listLegs(Long postingId);

    AccountPostingLegResponse getLeg(Long postingId, Long postingLegId);

    AccountPostingLegResponse updateLeg(Long postingId, Long postingLegId, UpdateLegRequest request);

    /**
     * Manually set leg status from the UI (mode=MANUAL, attemptNumber unchanged).
     */
    AccountPostingLegResponse manualUpdateLeg(Long postingId, Long postingLegId, LegStatus newStatus);

    /**
     * Returns all non-SUCCESS legs for a posting ordered by legOrder.
     * Used by the retry processor to find legs that need re-execution.
     */
    List<AccountPostingLegResponse> listNonSuccessLegs(Long postingId);
}
