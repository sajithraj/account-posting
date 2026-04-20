package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;

import java.util.List;

public interface AccountPostingLegService {

    AccountPostingLegEntity createLeg(Long postingId, int transactionOrder, String targetSystem,
                                      String account, String operation, String mode, int ttlDays);

    void updateLeg(Long postingId, int transactionOrder, String status,
                   String referenceId, String postedTime, String reason,
                   String requestPayload, String responsePayload, boolean isRetry);

    void manualUpdateLeg(Long postingId, int transactionOrder,
                         String status, String reason, String requestedBy);

    List<LegResponse> listLegs(Long postingId);

    LegResponse getLeg(Long postingId, int transactionOrder);

    List<AccountPostingLegEntity> listNonSuccessLegs(Long postingId);
}
