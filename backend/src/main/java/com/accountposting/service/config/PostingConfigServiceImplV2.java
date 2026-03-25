package com.accountposting.service.config;

import com.accountposting.config.CacheConfig;
import com.accountposting.dto.config.PostingConfigRequestV2;
import com.accountposting.dto.config.PostingConfigResponseV2;
import com.accountposting.entity.PostingConfig;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.MappingUtilsV2;
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
public class PostingConfigServiceImplV2 implements PostingConfigServiceV2 {

    private final PostingConfigRepository repository;
    private final CacheManager cacheManager;
    private final MappingUtilsV2 mappingUtils;

    @Override
    @Transactional(readOnly = true)
    public List<PostingConfigResponseV2> getAll() {
        log.info("GET ALL CONFIGS REQUEST");
        List<PostingConfigResponseV2> result = repository.findAll().stream()
                .map(this::toResponse)
                .toList();
        log.info("GET ALL CONFIGS RESPONSE | {}", mappingUtils.toJson(result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, key = "#requestType")
    public List<PostingConfigResponseV2> getByRequestType(String requestType) {
        log.info("GET CONFIG BY REQUEST TYPE REQUEST | requestType={}", requestType);
        List<PostingConfigResponseV2> result = repository.findByRequestTypeOrderByOrderSeqAsc(requestType)
                .stream()
                .map(this::toResponse)
                .toList();
        log.info("GET CONFIG BY REQUEST TYPE RESPONSE | requestType={} {}", requestType, mappingUtils.toJson(result));
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponseV2 create(PostingConfigRequestV2 request) {
        log.info("CREATE CONFIG REQUEST | {}", mappingUtils.toJson(request));
        PostingConfig config = PostingConfig.builder()
                .sourceName(request.getSourceName())
                .requestType(request.getRequestType())
                .targetSystem(request.getTargetSystem())
                .operation(request.getOperation())
                .orderSeq(request.getOrderSeq())
                .build();
        PostingConfigResponseV2 response = toResponse(repository.save(config));
        log.info("CREATE CONFIG RESPONSE | {}", mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponseV2 update(Long configId, PostingConfigRequestV2 request) {
        log.info("UPDATE CONFIG REQUEST | configId={} {}", configId, mappingUtils.toJson(request));
        PostingConfig config = repository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("PostingConfig", configId));
        config.setSourceName(request.getSourceName());
        config.setRequestType(request.getRequestType());
        config.setTargetSystem(request.getTargetSystem());
        config.setOperation(request.getOperation());
        config.setOrderSeq(request.getOrderSeq());
        PostingConfigResponseV2 response = toResponse(repository.save(config));
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

    private PostingConfigResponseV2 toResponse(PostingConfig config) {
        PostingConfigResponseV2 resp = new PostingConfigResponseV2();
        resp.setConfigId(config.getConfigId());
        resp.setSourceName(config.getSourceName());
        resp.setRequestType(config.getRequestType());
        resp.setTargetSystem(config.getTargetSystem());
        resp.setOperation(config.getOperation());
        resp.setOrderSeq(config.getOrderSeq());
        return resp;
    }
}
