package com.sajith.payments.redesign.service.accountpostingleg;

import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.ManualUpdateRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.UpdateLegRequestV2;

import java.util.List;

public interface AccountPostingLegServiceV2 {

    AccountPostingLegResponseV2 addLeg(Long postingId, AccountPostingLegRequestV2 request);

    List<AccountPostingLegResponseV2> listLegs(Long postingId);

    AccountPostingLegResponseV2 getLeg(Long postingId, Long postingLegId);

    AccountPostingLegResponseV2 updateLeg(Long postingId, Long postingLegId, UpdateLegRequestV2 request);

    AccountPostingLegResponseV2 manualUpdateLeg(Long postingId, Long transactionId, ManualUpdateRequestV2 request);

    List<AccountPostingLegResponseV2> listNonSuccessLegs(Long postingId);
}
