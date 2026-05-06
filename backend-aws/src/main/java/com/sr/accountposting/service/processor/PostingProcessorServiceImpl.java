package com.sr.accountposting.service.processor;

import com.sr.accountposting.dto.ExternalCallResult;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.entity.posting.AccountPostingEntity;
import com.sr.accountposting.enums.LegStatus;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.enums.RequestMode;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.posting.AccountPostingRepository;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.strategy.PostingStrategy;
import com.sr.accountposting.service.strategy.PostingStrategyFactory;
import com.sr.accountposting.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class PostingProcessorServiceImpl implements PostingProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PostingProcessorServiceImpl.class);

    private final AccountPostingRepository postingRepo;
    private final AccountPostingLegService legService;
    private final PostingStrategyFactory strategyFactory;

    @Inject
    public PostingProcessorServiceImpl(AccountPostingRepository postingRepo,
                                       AccountPostingLegService legService,
                                       PostingStrategyFactory strategyFactory) {
        this.postingRepo = postingRepo;
        this.legService = legService;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public ProcessingResult process(PostingJob job, List<PostingConfigEntity> configs) {
        String postingId = job.getPostingId();
        RequestMode mode = job.getRequestMode();

        AccountPostingEntity posting = postingRepo.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));

        String targetSystems = buildTargetSystems(configs);
        posting.setTargetSystems(targetSystems);

        List<AccountPostingLegEntity> legsToProcess;

        if (mode == RequestMode.RETRY) {
            List<AccountPostingLegEntity> allLegs = legService.listAllLegEntities(postingId);
            if (allLegs.isEmpty()) {
                log.warn("Posting [id={}] RETRY - no legs found, creating {} leg(s) from config",
                        postingId, configs.size());
                allLegs = new ArrayList<>();
                for (PostingConfigEntity config : configs) {
                    allLegs.add(legService.createLeg(postingId, config.getOrderSeq(),
                            config.getTargetSystem(), job.getRequestPayload().getDebtorAccount(),
                            config.getOperation(), "RETRY", AppConfig.TTL_DAYS));
                }
            }
            legsToProcess = allLegs.stream()
                    .filter(l -> !LegStatus.SUCCESS.name().equals(l.getStatus()))
                    .collect(Collectors.toList());
            log.info("Posting [id={}] RETRY - {} total leg(s), {} unprocessed to run",
                    postingId, allLegs.size(), legsToProcess.size());
        } else {
            log.info("Posting [id={}] NORM - creating {} leg(s) for target system(s): {}",
                    postingId, configs.size(), targetSystems);
            for (PostingConfigEntity config : configs) {
                legService.createLeg(postingId, config.getOrderSeq(),
                        config.getTargetSystem(), job.getRequestPayload().getDebtorAccount(),
                        config.getOperation(), "NORM", AppConfig.TTL_DAYS);
            }
            legsToProcess = legService.listNonSuccessLegs(postingId);
            log.info("Posting [id={}] NORM - {} unprocessed leg(s) to run", postingId, legsToProcess.size());
        }

        boolean allSuccess = true;
        String lastFailReason = null;
        List<ProcessingResult.LegFailure> failures = new ArrayList<>();

        for (AccountPostingLegEntity leg : legsToProcess) {
            PostingConfigEntity config = findConfigForLeg(configs, leg.getTargetSystem(), leg.getOperation());
            if (config == null) {
                String reason = "No routing config found for targetSystem=" + leg.getTargetSystem()
                        + " operation=" + leg.getOperation();
                log.error("Posting [id={}] leg [order={} system={}] - {}", postingId,
                        leg.getTransactionOrder(), leg.getTargetSystem(), reason);
                allSuccess = false;
                lastFailReason = reason;
                failures.add(ProcessingResult.LegFailure.builder()
                        .targetSystem(leg.getTargetSystem()).reason(reason).build());
                continue;
            }

            try {
                PostingStrategy strategy = strategyFactory.get(leg.getTargetSystem(), config.getOperation());
                ExternalCallResult result = strategy.process(job.getRequestPayload(), config);

                legService.updateLeg(postingId, leg.getTransactionOrder(),
                        result.getStatus().name(), result.getReferenceId(), result.getPostedTime(),
                        result.getReason(), result.getRequestPayload(), result.getResponsePayload(),
                        mode == RequestMode.RETRY);

                if (result.getStatus() == LegStatus.SUCCESS) {
                    log.info("Posting [id={}] leg [order={} system={}] posted successfully, ref={}",
                            postingId, leg.getTransactionOrder(), leg.getTargetSystem(), result.getReferenceId());
                } else {
                    log.warn("Posting [id={}] leg [order={} system={}] failed: {}",
                            postingId, leg.getTransactionOrder(), leg.getTargetSystem(), result.getReason());
                    allSuccess = false;
                    lastFailReason = result.getReason();
                    failures.add(ProcessingResult.LegFailure.builder()
                            .targetSystem(leg.getTargetSystem()).reason(result.getReason()).build());
                }
            } catch (Exception e) {
                log.error("Posting [id={}] leg [order={} system={}] strategy threw exception",
                        postingId, leg.getTransactionOrder(), leg.getTargetSystem(), e);
                allSuccess = false;
                lastFailReason = e.getMessage();
                failures.add(ProcessingResult.LegFailure.builder()
                        .targetSystem(leg.getTargetSystem()).reason(e.getMessage()).build());
            }
        }

        PostingStatus finalStatus = allSuccess ? PostingStatus.ACSP : PostingStatus.PNDG;
        String updatedAt = Instant.now().toString();
        posting.setStatus(finalStatus.name());
        posting.setReason(allSuccess ? null : lastFailReason);
        posting.setUpdatedAt(updatedAt);
        posting.setUpdatedBy("SYSTEM");
        posting.setRetryLockedUntil(null);
        postingRepo.update(posting);

        log.info("Posting [id={}] processing complete - status={}, legs processed={}, failed={}",
                postingId, finalStatus, legsToProcess.size(), failures.size());

        return ProcessingResult.builder()
                .status(finalStatus)
                .reason(allSuccess ? null : lastFailReason)
                .updatedAt(updatedAt)
                .failures(failures)
                .build();
    }

    private String buildTargetSystems(List<PostingConfigEntity> configs) {
        StringBuilder sb = new StringBuilder();
        configs.forEach(c -> {
            if (sb.length() > 0) sb.append("_");
            sb.append(c.getTargetSystem());
        });
        return sb.toString();
    }

    private PostingConfigEntity findConfigForLeg(List<PostingConfigEntity> configs,
                                                 String targetSystem, String operation) {
        return configs.stream()
                .filter(c -> c.getTargetSystem().equals(targetSystem)
                        && c.getOperation().equals(operation))
                .findFirst().orElse(null);
    }
}
