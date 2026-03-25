package com.accountposting.service.config;

import com.accountposting.dto.config.PostingConfigRequestV2;
import com.accountposting.dto.config.PostingConfigResponseV2;

import java.util.List;

public interface PostingConfigServiceV2 {

    List<PostingConfigResponseV2> getByRequestType(String requestType);

    List<PostingConfigResponseV2> getAll();

    PostingConfigResponseV2 create(PostingConfigRequestV2 request);

    PostingConfigResponseV2 update(Long configId, PostingConfigRequestV2 request);

    void delete(Long configId);

    void flushCache();
}
