package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.PostingSearchRequest;
import com.sr.accountposting.dto.posting.PostingSearchResponse;
import com.sr.accountposting.dto.posting.RetryRequest;
import com.sr.accountposting.dto.posting.RetryResponse;

public interface AccountPostingService {

    PostingResponse findById(String postingId);

    PostingSearchResponse search(PostingSearchRequest searchRequest);

    RetryResponse retry(RetryRequest request);
}
