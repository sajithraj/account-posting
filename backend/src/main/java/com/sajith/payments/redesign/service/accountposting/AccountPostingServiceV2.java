package com.sajith.payments.redesign.service.accountposting;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingRequestV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingSearchRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountPostingServiceV2 {

    AccountPostingCreateResponseV2 create(AccountPostingRequestV2 request);

    Page<AccountPostingFullResponseV2> search(AccountPostingSearchRequestV2 criteria, Pageable pageable);

    AccountPostingFullResponseV2 findById(Long postingId);

    RetryResponseV2 retry(RetryRequestV2 request);

    Page<AccountPostingFullResponseV2> searchHistory(AccountPostingSearchRequestV2 criteria, Pageable pageable);
}
