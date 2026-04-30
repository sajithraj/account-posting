package com.sr.accountposting.service.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.config.PostingConfigRepository;
import com.sr.accountposting.util.IdGenerator;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

@Singleton
public class PostingConfigServiceImpl implements PostingConfigService {

    private static final Logger log = LoggerFactory.getLogger(PostingConfigServiceImpl.class);

    private final PostingConfigRepository configRepo;

    @Inject
    public PostingConfigServiceImpl(PostingConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public List<PostingConfigEntity> getAll() {
        log.info("Config API request: getAll");
        List<PostingConfigEntity> result = configRepo.findAll();
        log.info("Config API response: getAll -> {}", JsonUtil.toJson(result));
        return result;
    }

    @Override
    public List<PostingConfigEntity> getByRequestType(String requestType) {
        log.info("Config API request: getByRequestType requestType={}", requestType);
        List<PostingConfigEntity> result = configRepo.findByRequestType(requestType);
        log.info("Config API response: getByRequestType requestType={} -> {}", requestType, JsonUtil.toJson(result));
        return result;
    }

    @Override
    public PostingConfigEntity create(PostingConfigEntity config) {
        log.info("Config API request: create -> {}", JsonUtil.toJson(config));
        if (configRepo.existsByRequestTypeAndOrderSeq(config.getRequestType(), config.getOrderSeq())) {
            throw new BusinessException("DUPLICATE_CONFIG",
                    "Config already exists for requestType=" + config.getRequestType()
                            + " orderSeq=" + config.getOrderSeq());
        }
        String now = Instant.now().toString();
        config.setConfigId(IdGenerator.nextId());
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        configRepo.save(config);
        log.info("Config API response: create configId={} -> {}", config.getConfigId(), JsonUtil.toJson(config));
        return config;
    }

    @Override
    public PostingConfigEntity update(String configId, PostingConfigEntity updated) {
        log.info("Config API request: update configId={} body={}", configId, JsonUtil.toJson(updated));
        PostingConfigEntity existing = configRepo.findByConfigId(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: configId=" + configId));

        existing.setTargetSystem(updated.getTargetSystem());
        existing.setOperation(updated.getOperation());
        existing.setSourceName(updated.getSourceName());
        existing.setProcessingMode(updated.getProcessingMode());
        existing.setUpdatedAt(Instant.now().toString());
        existing.setUpdatedBy(updated.getUpdatedBy());
        configRepo.save(existing);
        log.info("Config API response: update configId={} -> {}", configId, JsonUtil.toJson(existing));
        return existing;
    }

    @Override
    public void delete(String configId) {
        log.info("Config API request: delete configId={}", configId);
        configRepo.findByConfigId(configId)
                .orElseThrow(() -> new ResourceNotFoundException("Config not found: configId=" + configId));
        configRepo.deleteByConfigId(configId);
        log.info("Config API response: delete configId={} -> deleted", configId);
    }
}
