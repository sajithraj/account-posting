package com.sr.accountposting.service.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;

import java.util.List;

public interface PostingConfigService {

    List<PostingConfigEntity> getAll();

    List<PostingConfigEntity> getByRequestType(String requestType);

    PostingConfigEntity create(PostingConfigEntity config);

    PostingConfigEntity update(String configId, PostingConfigEntity updated);

    void delete(String configId);
}
