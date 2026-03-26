package com.sajith.payments.redesign.service.accountpostingleg;

import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.UpdateLegRequestV2;
import com.sajith.payments.redesign.entity.enums.LegStatus;

import java.util.List;

public interface AccountPostingLegServiceV2 {

    AccountPostingLegResponseV2 addLeg(Long postingId, AccountPostingLegRequestV2 request);

    List<AccountPostingLegResponseV2> listLegs(Long postingId);

    AccountPostingLegResponseV2 getLeg(Long postingId, Long postingLegId);

    AccountPostingLegResponseV2 updateLeg(Long postingId, Long postingLegId, UpdateLegRequestV2 request);

    /**
     * Manually set leg status from the UI (mode=MANUAL, attemptNumber unchanged).
     * Optional reason is persisted to the leg record so the UI always shows the latest explanation.
     */
    AccountPostingLegResponseV2 manualUpdateLeg(Long postingId, Long postingLegId, LegStatus newStatus, String reason);

    /**
     * Returns all non-SUCCESS legs for a posting ordered by legOrder.
     * Used by the retry processor to find legs that need re-execution.
     */
    List<AccountPostingLegResponseV2> listNonSuccessLegs(Long postingId);
}
