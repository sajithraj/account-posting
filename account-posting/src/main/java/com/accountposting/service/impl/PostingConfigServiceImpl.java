package com.accountposting.service.impl;

import com.accountposting.config.CacheConfig;
import com.accountposting.dto.config.PostingConfigRequest;
import com.accountposting.dto.config.PostingConfigResponse;
import com.accountposting.entity.PostingConfig;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.service.PostingConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostingConfigServiceImpl implements PostingConfigService {

    private final PostingConfigRepository repository;
    private final CacheManager cacheManager;

    @Override
    @Transactional(readOnly = true)
    public List<PostingConfigResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, key = "#requestType")
    public List<PostingConfigResponse> getByRequestType(String requestType) {
        return repository.findByRequestTypeOrderByOrderSeqAsc(requestType)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponse create(PostingConfigRequest request) {
        PostingConfig config = PostingConfig.builder()
                .sourceName(request.getSourceName())
                .requestType(request.getRequestType())
                .targetSystem(request.getTargetSystem())
                .operation(request.getOperation())
                .orderSeq(request.getOrderSeq())
                .build();
        return toResponse(repository.save(config));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponse update(Long configId, PostingConfigRequest request) {
        PostingConfig config = repository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("PostingConfig", configId));
        config.setSourceName(request.getSourceName());
        config.setRequestType(request.getRequestType());
        config.setTargetSystem(request.getTargetSystem());
        config.setOperation(request.getOperation());
        config.setOrderSeq(request.getOrderSeq());
        return toResponse(repository.save(config));
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public void delete(Long configId) {
        if (!repository.existsById(configId)) {
            throw new ResourceNotFoundException("PostingConfig", configId);
        }
        repository.deleteById(configId);
    }

    @Override
    public void flushCache() {
        var cache = cacheManager.getCache(CacheConfig.CONFIG_BY_REQUEST_TYPE);
        if (cache != null) {
            cache.clear();
            log.info("Flushed cache: {}", CacheConfig.CONFIG_BY_REQUEST_TYPE);
        }
    }

    private PostingConfigResponse toResponse(PostingConfig config) {
        PostingConfigResponse resp = new PostingConfigResponse();
        resp.setConfigId(config.getConfigId());
        resp.setSourceName(config.getSourceName());
        resp.setRequestType(config.getRequestType());
        resp.setTargetSystem(config.getTargetSystem());
        resp.setOperation(config.getOperation());
        resp.setOrderSeq(config.getOrderSeq());
        return resp;
    }
}
