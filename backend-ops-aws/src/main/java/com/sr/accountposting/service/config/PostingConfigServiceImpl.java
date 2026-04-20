package com.sr.accountposting.service.config;

import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.config.PostingConfigRepository;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;

@Singleton
public class PostingConfigServiceImpl implements PostingConfigService {

    private final PostingConfigRepository configRepo;

    @Inject
    public PostingConfigServiceImpl(PostingConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    @Override
    public List<PostingConfigEntity> getAll() {
        return configRepo.findAll();
    }

    @Override
    public List<PostingConfigEntity> getByRequestType(String requestType) {
        return configRepo.findByRequestType(requestType);
    }

    @Override
    public PostingConfigEntity create(PostingConfigEntity config) {
        if (configRepo.existsByRequestTypeAndOrderSeq(config.getRequestType(), config.getOrderSeq())) {
            throw new BusinessException("DUPLICATE_CONFIG",
                    "Config already exists for requestType=" + config.getRequestType()
                            + " orderSeq=" + config.getOrderSeq());
        }
        String now = Instant.now().toString();
        config.setCreatedAt(now);
        config.setUpdatedAt(now);
        configRepo.save(config);
        return config;
    }

    @Override
    public PostingConfigEntity update(String requestType, Integer orderSeq, PostingConfigEntity updated) {
        PostingConfigEntity existing = configRepo.findByRequestTypeAndOrderSeq(requestType, orderSeq)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Config not found: requestType=" + requestType + " orderSeq=" + orderSeq));

        existing.setTargetSystem(updated.getTargetSystem());
        existing.setOperation(updated.getOperation());
        existing.setSourceName(updated.getSourceName());
        existing.setUpdatedAt(Instant.now().toString());
        existing.setUpdatedBy(updated.getUpdatedBy());
        configRepo.save(existing);
        return existing;
    }

    @Override
    public void delete(String requestType, Integer orderSeq) {
        configRepo.findByRequestTypeAndOrderSeq(requestType, orderSeq)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Config not found: requestType=" + requestType + " orderSeq=" + orderSeq));
        configRepo.delete(requestType, orderSeq);
    }
}
