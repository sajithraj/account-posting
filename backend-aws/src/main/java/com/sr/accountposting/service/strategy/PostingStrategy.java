package com.sr.accountposting.service.strategy;

import com.sr.accountposting.dto.ExternalCallResult;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.entity.config.PostingConfigEntity;

public interface PostingStrategy {

    String getFlowKey();

    ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config);
}
