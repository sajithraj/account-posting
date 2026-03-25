package com.accountposting.service.accountposting;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountposting.AccountPostingResponseV2;
import com.accountposting.dto.accountposting.AccountPostingSearchRequestV2;
import com.accountposting.dto.retry.RetryRequestV2;
import com.accountposting.dto.retry.RetryResponseV2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountPostingServiceV2 {

    AccountPostingCreateResponseV2 create(AccountPostingRequestV2 request);

    Page<AccountPostingResponseV2> search(AccountPostingSearchRequestV2 criteria, Pageable pageable);

    /**
     * Looks up an active posting first; transparently falls back to the history table
     * if the posting has been archived.
     */
    AccountPostingResponseV2 findById(Long postingId);

    RetryResponseV2 retry(RetryRequestV2 request);

    /**
     * Searches the history table only. Accepts the same filter criteria as {@link #search}.
     */
    Page<AccountPostingResponseV2> searchHistory(AccountPostingSearchRequestV2 criteria, Pageable pageable);
}
