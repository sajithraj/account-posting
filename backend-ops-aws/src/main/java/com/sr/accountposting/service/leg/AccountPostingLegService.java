package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;

public interface AccountPostingLegService {

    void manualUpdateLeg(String postingId, Integer transactionOrder,
                         String status, String reason, String requestedBy);

    LegResponse getLeg(String postingId, Integer transactionOrder);
}
