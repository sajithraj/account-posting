package com.sr.accountposting.service.strategy;

import com.sr.accountposting.dto.ExternalCallResult;
import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.entity.config.PostingConfigEntity;

public interface PostingStrategy {

    /**
     * e.g. "CBS_POSTING", "GL_POSTING", "OBPM_POSTING", "CBS_ADD_HOLD", "CBS_REMOVE_HOLD"
     */
    String getFlowKey();

    ExternalCallResult process(IncomingPostingRequest request, PostingConfigEntity config);
}
