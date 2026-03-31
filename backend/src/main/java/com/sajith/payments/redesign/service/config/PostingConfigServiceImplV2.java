package com.sajith.payments.redesign.service.config;

import com.sajith.payments.redesign.config.CacheConfig;
import com.sajith.payments.redesign.dto.config.PostingConfigRequestV2;
import com.sajith.payments.redesign.dto.config.PostingConfigResponseV2;
import com.sajith.payments.redesign.entity.PostingConfig;
import com.sajith.payments.redesign.exception.BusinessException;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.mapper.PostingConfigMapperV2;
import com.sajith.payments.redesign.repository.PostingConfigRepository;
import com.sajith.payments.redesign.utils.AppUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        PostingConfig entity = mapper.toEntity(request);
        String auditor = requestedBy(request.getRequestedBy());
        entity.setCreatedBy(auditor);
        entity.setUpdatedBy(auditor);
        PostingConfigResponseV2 response = mapper.toResponse(repository.save(entity));
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
        config.setUpdatedBy(requestedBy(request.getRequestedBy()));
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

    private String requestedBy(String value) {
        return (value != null && !value.isBlank()) ? value : "SYSTEM";
    }

}
