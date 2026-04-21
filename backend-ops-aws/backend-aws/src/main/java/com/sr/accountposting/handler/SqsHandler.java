package com.sr.accountposting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.sr.accountposting.dto.posting.PostingJob;
import com.sr.accountposting.dto.posting.ProcessingResult;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.enums.PostingStatus;
import com.sr.accountposting.repository.config.PostingConfigRepository;
import com.sr.accountposting.service.processor.PostingProcessorService;
import com.sr.accountposting.util.AppConfig;
import com.sr.accountposting.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Singleton
public class SqsHandler {

    private static final Logger log = LoggerFactory.getLogger(SqsHandler.class);

    private final PostingProcessorService processor;
    private final PostingConfigRepository configRepo;
    private final SnsClient snsClient;

    @Inject
    public SqsHandler(PostingProcessorService processor,
                      PostingConfigRepository configRepo,
                      SnsClient snsClient) {
        this.processor = processor;
        this.configRepo = configRepo;
        this.snsClient = snsClient;
    }

    @SuppressWarnings("unchecked")
    public void handle(Map<String, Object> event, Context context) {
        List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
        if (records == null || records.isEmpty()) {
            log.warn("SQS event received with no Records");
            return;
        }
        for (Map<String, Object> record : records) {
            String messageId = (String) record.get("messageId");
            String body = (String) record.get("body");
            try {
                PostingJob job = JsonUtil.fromJson(body, PostingJob.class);
                log.info("SQS message received — posting [id={}] mode={}", job.getPostingId(), job.getRequestMode());
                processJob(job);
            } catch (Exception e) {
                log.error("Failed to process SQS message (messageId={}) — posting stays PNDG for dashboard retry",
                        messageId, e);
            }
        }
    }

    private void processJob(PostingJob job) {
        List<PostingConfigEntity> configs = configRepo.findByRequestType(
                job.getRequestPayload().getRequestType());

        if (configs.isEmpty()) {
            log.error("Posting [id={}] — no routing configs found for requestType={}, alerting support",
                    job.getPostingId(), job.getRequestPayload().getRequestType());
            alertSupportTeam(job.getPostingId(),
                    job.getRequestPayload().getEndToEndReferenceId(),
                    "N/A", "No routing configs found for requestType="
                            + job.getRequestPayload().getRequestType());
            return;
        }

        ProcessingResult result = processor.process(job, configs);

        if (result.getStatus() != PostingStatus.ACSP && !result.getFailures().isEmpty()) {
            String e2eRef = job.getRequestPayload().getEndToEndReferenceId();
            log.warn("Posting [id={}] finished with {} failure(s) — alerting support team",
                    job.getPostingId(), result.getFailures().size());
            result.getFailures().forEach(f ->
                    alertSupportTeam(job.getPostingId(), e2eRef, f.getTargetSystem(), f.getReason()));
        }

        log.info("SQS job done — posting [id={}] final status={}", job.getPostingId(), result.getStatus());
    }

    private void alertSupportTeam(Long postingId, String e2eRef, String targetSystem, String reason) {
        String message = String.format(
                "Account Posting Failed — Action Required%n" +
                        "postingId:    %d%n" +
                        "e2eReference: %s%n" +
                        "targetSystem: %s%n" +
                        "reason:       %s%n" +
                        "timestamp:    %s",
                postingId, e2eRef, targetSystem, reason, Instant.now());
        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(AppConfig.SNS_TOPIC_ARN)
                    .subject("Posting Failure Alert - postingId=" + postingId)
                    .message(message)
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish SNS alert for postingId={}", postingId, e);
        }
    }
}
