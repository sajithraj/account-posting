package com.sr.accountposting.service.posting;

import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.PostingResponse;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.exception.BusinessException;
import com.sr.accountposting.exception.TechnicalException;
import com.sr.accountposting.exception.ValidationException;
import com.sr.accountposting.repository.config.PostingConfigRepository;
import com.sr.accountposting.repository.posting.AccountPostingRepository;
import com.sr.accountposting.service.processor.PostingProcessorService;
import com.sr.accountposting.util.AppConfig;
import com.sr.accountposting.util.IdGenerator;
import com.sr.accountposting.util.JsonUtil;
import com.sr.accountposting.validator.AccountPostingValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class AccountPostingServiceImpl implements AccountPostingService {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingServiceImpl.class);

    private final AccountPostingRepository postingRepo;
    private final PostingConfigRepository configRepo;
    private final PostingProcessorService processor;
    private final AccountPostingValidator validator;
    private final SqsClient sqsClient;

    @Inject
    public AccountPostingServiceImpl(AccountPostingRepository postingRepo,
                                     PostingConfigRepository configRepo,
                                     PostingProcessorService processor,
                                     AccountPostingValidator validator,
                                     SqsClient sqsClient) {
        this.postingRepo = postingRepo;
        this.configRepo = configRepo;
        this.processor = processor;
        this.validator = validator;
        this.sqsClient = sqsClient;
    }

    @Override
    public PostingResponse create(IncomingPostingRequest request) {
        log.info("Incoming posting create {} .", request);
        List<PostingConfigEntity> configs = configRepo.findByRequestType(request.getRequestType());
        if (configs.isEmpty()) {
            throw new ValidationException("UNKNOWN_REQUEST_TYPE",
                    "Unknown or unconfigured requestType: " + request.getRequestType());
        }

        validator.validate(request, configs);

        if (postingRepo.existsByEndToEndReferenceId(request.getEndToEndReferenceId())) {
            throw new BusinessException("DUPLICATE_E2E_REF",
                    "Posting already exists for endToEndReferenceId: " + request.getEndToEndReferenceId());
        }

        Set<String> modes = configs.stream()
                .map(PostingConfigEntity::getProcessingMode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        boolean isAsync = modes.size() == 1 && modes.contains("ASYNC");

        long postingId = IdGenerator.nextId();
        String now = Instant.now().toString();
        AccountPostingEntity posting = buildPosting(postingId, request,
                isAsync ? PostingStatus.RECEIVED : PostingStatus.PNDG, now);

        try {
            postingRepo.save(posting);
            log.info("Posting [id={}] accepted — requestType={} sourceName={} isAsync={}",
                    postingId, request.getRequestType(), request.getSourceName(), isAsync);
        } catch (Exception e) {
            throw new TechnicalException("Failed to persist posting [id=" + postingId + "]", e);
        }

        PostingJob job = PostingJob.builder()
                .postingId(postingId)
                .requestPayload(request)
                .requestMode(RequestMode.NORM)
                .build();

        if (isAsync) {
            try {
                publishJob(job);
            } catch (Exception e) {
                throw new TechnicalException(
                        "Posting [id=" + postingId + "] saved but failed to enqueue — caller should retry", e);
            }
            return PostingResponse.builder()
                    .postingStatus(PostingStatus.ACSP.name())
                    .endToEndReferenceId(request.getEndToEndReferenceId())
                    .sourceReferenceId(request.getSourceReferenceId())
                    .processedAt(now)
                    .build();
        } else {
            ProcessingResult result = processor.process(job, configs);
            return PostingResponse.builder()
                    .postingStatus(result.getStatus().name())
                    .endToEndReferenceId(request.getEndToEndReferenceId())
                    .sourceReferenceId(request.getSourceReferenceId())
                    .processedAt(result.getUpdatedAt())
                    .build();
        }
    }

    private void publishJob(PostingJob job) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(AppConfig.QUEUE_URL)
                .messageBody(JsonUtil.toJson(job))
                .build());
        log.info("Posting [id={}] queued for {} processing", job.getPostingId(), job.getRequestMode());
    }

    private AccountPostingEntity buildPosting(long postingId, IncomingPostingRequest req,
                                              PostingStatus status, String now) {
        AccountPostingEntity p = new AccountPostingEntity();
        p.setPostingId(postingId);
        p.setSourceReferenceId(req.getSourceReferenceId());
        p.setEndToEndReferenceId(req.getEndToEndReferenceId());
        p.setSourceName(req.getSourceName());
        p.setRequestType(req.getRequestType());
        p.setAmount(req.getAmount() != null ? req.getAmount().getValue() : null);
        p.setCurrency(req.getAmount() != null ? req.getAmount().getCurrencyCode() : null);
        p.setCreditDebitIndicator(req.getCreditDebitIndicator());
        p.setDebtorAccount(req.getDebtorAccount());
        p.setCreditorAccount(req.getCreditorAccount());
        p.setRequestedExecutionDate(req.getRequestedExecutionDate());
        p.setRemittanceInformation(req.getRemittanceInformation());
        p.setStatus(status.name());
        p.setRequestPayload(JsonUtil.toJson(req));
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setCreatedBy("SYSTEM");
        p.setUpdatedBy("SYSTEM");
        p.setTtl(IdGenerator.ttlEpochSeconds(AppConfig.TTL_DAYS));
        return p;
    }
}
