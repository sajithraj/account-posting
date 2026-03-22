package com.accountposting.service;


import com.accountposting.dto.config.PostingConfigRequest;
import com.accountposting.dto.config.PostingConfigResponse;

import java.util.List;

public interface PostingConfigService {

    List<PostingConfigResponse> getByRequestType(String requestType);

    List<PostingConfigResponse> getAll();

    PostingConfigResponse create(PostingConfigRequest request);

    PostingConfigResponse update(Long configId, PostingConfigRequest request);

    void delete(Long configId);

    void flushCache();
}
