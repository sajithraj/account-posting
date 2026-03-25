package com.accountposting.service.config;

import com.accountposting.config.CacheConfig;
import com.accountposting.dto.config.PostingConfigRequestV2;
import com.accountposting.dto.config.PostingConfigResponseV2;
import com.accountposting.entity.PostingConfig;
import com.accountposting.exception.BusinessException;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.PostingConfigMapperV2;
import com.accountposting.repository.PostingConfigRepository;
import com.accountposting.utils.AppUtility;
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
    private final PostingConfigMapperV2 mapper;
    private final CacheManager cacheManager;
    private final AppUtility appUtility;

    @Override
    @Transactional(readOnly = true)
    public List<PostingConfigResponseV2> getAll() {
        log.info("Request received to get all configs.");
        List<PostingConfigResponseV2> result = repository.findAll().stream()
                .map(mapper::toResponse)
                .toList();
        log.info("Response to send for get all configs :: {} .", appUtility.toObjectToString(result));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, key = "#requestType")
    public List<PostingConfigResponseV2> getByRequestType(String requestType) {
        log.info("Request received to get config for request type :: {} .", requestType);
        List<PostingConfigResponseV2> result = repository.findByRequestTypeOrderByOrderSeqAsc(requestType)
                .stream()
                .map(mapper::toResponse)
                .toList();
        log.info("Response config to send for  request type :: {} and config details :: {}  .", requestType, appUtility.toObjectToString(result));
        return result;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponseV2 create(PostingConfigRequestV2 request) {
        log.info("Request received to create config. Received request :: {} .", appUtility.toObjectToString(request));
        if (repository.existsByRequestTypeAndOrderSeq(request.getRequestType(), request.getOrderSeq())) {
            throw new BusinessException("DUPLICATE_CONFIG_ORDER",
                    "order_seq " + request.getOrderSeq() + " already exists for request_type " + request.getRequestType());
        }
        PostingConfigResponseV2 response = mapper.toResponse(repository.save(mapper.toEntity(request)));
        log.info("Response to send for create config :: {} .", appUtility.toObjectToString(response));
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public PostingConfigResponseV2 update(Long configId, PostingConfigRequestV2 request) {
        log.info("Request received to update config for config id {} . Received request :: {} .", configId, appUtility.toObjectToString(request));
        PostingConfig config = repository.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("PostingConfig", configId));
        if (repository.existsByRequestTypeAndOrderSeqAndConfigIdNot(request.getRequestType(), request.getOrderSeq(), configId)) {
            throw new BusinessException("DUPLICATE_CONFIG_ORDER",
                    "order_seq " + request.getOrderSeq() + " already exists for request_type " + request.getRequestType());
        }
        config.setSourceName(request.getSourceName());
        config.setRequestType(request.getRequestType());
        config.setTargetSystem(request.getTargetSystem());
        config.setOperation(request.getOperation());
        config.setOrderSeq(request.getOrderSeq());
        PostingConfigResponseV2 response = mapper.toResponse(repository.save(config));
        log.info("Response to send for update config for config id {} . Config details :: {} .", configId, appUtility.toObjectToString(response));
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(value = CacheConfig.CONFIG_BY_REQUEST_TYPE, allEntries = true)
    public void delete(Long configId) {
        log.info("Request received to delete config for config id {} .", configId);
        if (!repository.existsById(configId)) {
            throw new ResourceNotFoundException("PostingConfig", configId);
        }
        repository.deleteById(configId);
        log.info("Successfully deleted the config for config id :: {} .", configId);
    }

    @Override
    public void flushCache() {
        var cache = cacheManager.getCache(CacheConfig.CONFIG_BY_REQUEST_TYPE);
        if (cache != null) {
            cache.clear();
            log.info("Flushed cache :: {} .", CacheConfig.CONFIG_BY_REQUEST_TYPE);
        }
    }

}
