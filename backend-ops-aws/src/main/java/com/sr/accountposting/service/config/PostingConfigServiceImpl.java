package com.sr.accountposting.service.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.config.PostingConfigRepository;
import com.sr.accountposting.util.IdGenerator;
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
        log.info("Config getAll started");
        List<PostingConfigEntity> result = configRepo.findAll();
        log.info("Config getAll completed | count={}", result.size());
        return result;
    }

    @Override
    public List<PostingConfigEntity> getByRequestType(String requestType) {
        log.info("Config getByRequestType started | requestType={}", requestType);
        List<PostingConfigEntity> result = configRepo.findByRequestType(requestType);
        log.info("Config getByRequestType completed | requestType={} count={}", requestType, result.size());
        return result;
    }

    @Override
    public PostingConfigEntity create(PostingConfigEntity config) {
        log.info("Config create started | requestType={} orderSeq={} targetSystem={} operation={}",
                config.getRequestType(), config.getOrderSeq(), config.getTargetSystem(), config.getOperation());
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
        log.info("Config create completed | configId={} requestType={} orderSeq={}",
                config.getConfigId(), config.getRequestType(), config.getOrderSeq());
        return config;
    }

    @Override
    public PostingConfigEntity update(String requestType, Integer orderSeq, PostingConfigEntity updated) {
        log.info("Config update started | requestType={} orderSeq={}", requestType, orderSeq);
        PostingConfigEntity existing = configRepo.findByRequestTypeAndOrderSeq(requestType, orderSeq)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Config not found: requestType=" + requestType + " orderSeq=" + orderSeq));

        log.debug("Config found for update | configId={} requestType={} orderSeq={}",
                existing.getConfigId(), requestType, orderSeq);

        existing.setTargetSystem(updated.getTargetSystem());
        existing.setOperation(updated.getOperation());
        existing.setSourceName(updated.getSourceName());
        existing.setProcessingMode(updated.getProcessingMode());
        existing.setUpdatedAt(Instant.now().toString());
        existing.setUpdatedBy(updated.getUpdatedBy());
        configRepo.save(existing);

        log.info("Config update completed | configId={} requestType={} orderSeq={} targetSystem={} processingMode={}",
                existing.getConfigId(), requestType, orderSeq,
                existing.getTargetSystem(), existing.getProcessingMode());
        return existing;
    }

    @Override
    public void delete(String requestType, Integer orderSeq) {
        log.info("Config delete started | requestType={} orderSeq={}", requestType, orderSeq);
        configRepo.findByRequestTypeAndOrderSeq(requestType, orderSeq)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Config not found: requestType=" + requestType + " orderSeq=" + orderSeq));
        configRepo.delete(requestType, orderSeq);
        log.info("Config delete completed | requestType={} orderSeq={}", requestType, orderSeq);
    }
}
