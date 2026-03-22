package com.accountposting.service.config;

import com.accountposting.config.CacheConfig;
import com.accountposting.dto.config.PostingConfigRequest;
import com.accountposting.dto.config.PostingConfigResponse;
import com.accountposting.entity.PostingConfig;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.MappingUtils;
import com.accountposting.repository.PostingConfigRepository;
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
    private final MappingUtils mappingUtils;

    @Override
    @Transactional(readOnly = true)
    public List<PostingConfigResponse> getAll() {
        log.info("GET ALL CONFIGS REQUEST");
        List<PostingConfigResponse> result = repository.findAll().stream()
                .map(this::toResponse)
                .toList();
        log.info("GET ALL CONFIGS RESPONSE | {}", mappingUtils.toJson(result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, key = "#requestType")
    public List<PostingConfigResponse> getByRequestType(String requestType) {
        log.info("GET CONFIG BY REQUEST TYPE REQUEST | requestType={}", requestType);
        List<PostingConfigResponse> result = repository.findByRequestTypeOrderByOrderSeqAsc(requestType)
                .stream()
                .map(this::toResponse)
                .toList();
        log.info("GET CONFIG BY REQUEST TYPE RESPONSE | requestType={} {}", requestType, mappingUtils.toJson(result));
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponse create(PostingConfigRequest request) {
        log.info("CREATE CONFIG REQUEST | {}", mappingUtils.toJson(request));
        PostingConfig config = PostingConfig.builder()
                .sourceName(request.getSourceName())
                .requestType(request.getRequestType())
                .targetSystem(request.getTargetSystem())
                .operation(request.getOperation())
                .orderSeq(request.getOrderSeq())
                .build();
        PostingConfigResponse response = toResponse(repository.save(config));
        log.info("CREATE CONFIG RESPONSE | {}", mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponse update(Long configId, PostingConfigRequest request) {
        log.info("UPDATE CONFIG REQUEST | configId={} {}", configId, mappingUtils.toJson(request));
        PostingConfig config = repository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("PostingConfig", configId));
        config.setSourceName(request.getSourceName());
        config.setRequestType(request.getRequestType());
        config.setTargetSystem(request.getTargetSystem());
        config.setOperation(request.getOperation());
        config.setOrderSeq(request.getOrderSeq());
        PostingConfigResponse response = toResponse(repository.save(config));
        log.info("UPDATE CONFIG RESPONSE | configId={} {}", configId, mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public void delete(Long configId) {
        log.info("DELETE CONFIG REQUEST | configId={}", configId);
        if (!repository.existsById(configId)) {
            throw new ResourceNotFoundException("PostingConfig", configId);
        }
        repository.deleteById(configId);
        log.info("DELETE CONFIG RESPONSE | configId={} deleted", configId);
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
