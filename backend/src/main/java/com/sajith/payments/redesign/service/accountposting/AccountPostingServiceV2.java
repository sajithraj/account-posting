package com.sajith.payments.redesign.service.accountposting;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import com.sajith.payments.redesign.dto.search.PostingSearchRequestV2;
import com.sajith.payments.redesign.dto.search.PostingSearchResponseV2;

public interface AccountPostingServiceV2 {

    AccountPostingCreateResponseV2 create(IncomingPostingRequest request);

    PostingSearchResponseV2 search(PostingSearchRequestV2 request);

    AccountPostingFullResponseV2 findById(Long postingId);

    RetryResponseV2 retry(RetryRequestV2 request);
}
