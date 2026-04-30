package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;

import java.util.List;

public interface AccountPostingLegService {

    void manualUpdateLeg(String postingId, String transactionId,
                         String status, String reason, String requestedBy);

    List<LegResponse> listLegs(String postingId);

    LegResponse getLeg(String postingId, String transactionId);
}
