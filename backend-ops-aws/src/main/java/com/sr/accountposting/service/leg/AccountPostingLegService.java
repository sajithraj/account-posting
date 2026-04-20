package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;

import java.util.List;

public interface AccountPostingLegService {

    void manualUpdateLeg(Long postingId, int transactionOrder,
                         String status, String reason, String requestedBy);

    List<LegResponse> listLegs(Long postingId);

    LegResponse getLeg(Long postingId, int transactionOrder);
}
