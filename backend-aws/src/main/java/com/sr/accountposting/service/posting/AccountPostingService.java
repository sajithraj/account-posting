package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingResponse;

public interface AccountPostingService {

    PostingResponse create(IncomingPostingRequest request);
}
