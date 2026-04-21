package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;

import java.util.List;

public interface AccountPostingLegService {

    AccountPostingLegEntity createLeg(String postingId, int transactionOrder, String targetSystem,
                                      String account, String operation, String mode, int ttlDays);

    void updateLeg(String postingId, int transactionOrder, String status,
                   String referenceId, String postedTime, String reason,
                   String requestPayload, String responsePayload, boolean isRetry);

    void manualUpdateLeg(String postingId, int transactionOrder,
                         String status, String reason, String requestedBy);

    List<LegResponse> listLegs(String postingId);

    LegResponse getLeg(String postingId, int transactionOrder);

    List<AccountPostingLegEntity> listNonSuccessLegs(String postingId);
}
