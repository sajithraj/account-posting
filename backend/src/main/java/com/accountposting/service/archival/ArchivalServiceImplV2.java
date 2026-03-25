package com.accountposting.service.archival;

import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.AccountPostingHistoryEntity;
import com.accountposting.entity.AccountPostingLegEntity;
import com.accountposting.entity.AccountPostingLegHistoryEntity;
import com.accountposting.repository.AccountPostingHistoryRepository;
import com.accountposting.repository.AccountPostingLegHistoryRepository;
import com.accountposting.repository.AccountPostingLegRepository;
import com.accountposting.repository.AccountPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled archival job.
 *
 * <h3>Flow (per batch)</h3>
 * <ol>
 *   <li>Query active {@code account_posting} rows whose {@code created_at} is older than
 *       {@code app.archival.threshold-days} days (default 90), up to {@code batch-size} rows.</li>
 *   <li>Bulk-load all associated {@code account_posting_leg} rows.</li>
 *   <li>Insert both sets into the history tables.</li>
 *   <li>Delete legs first (FK constraint), then postings.</li>
 *   <li>Each batch runs in its own committed transaction via {@link TransactionTemplate}
 *       — consistent with the retry pattern already used in this project.</li>
 * </ol>
 *
 * <h3>Why 90 days / configurable threshold?</h3>
 * The threshold is driven by operational retention policy.  Setting
 * {@code app.archival.threshold-days=80} would start archiving records at the 80-day mark,
 * giving a 10-day safety buffer before a 90-day hard SLA.
 *
 * <h3>Configuration</h3>
 * <pre>
 * app.archival.enabled=true
 * app.archival.cron=0 0 2 * * ?    # daily at 02:00 server time
 * app.archival.threshold-days=90   # archive records older than this
 * app.archival.batch-size=500
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchivalServiceImplV2 implements ArchivalServiceV2 {

    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegRepository legRepository;
    private final AccountPostingHistoryRepository historyRepository;
    private final AccountPostingLegHistoryRepository legHistoryRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${app.archival.enabled:true}")
    private boolean enabled;

    @Value("${app.archival.threshold-days:90}")
    private int thresholdDays;

    @Value("${app.archival.batch-size:500}")
    private int batchSize;

    /**
     * Entry point called by Spring Scheduler.
     * Loops until no more eligible rows exist, committing one batch per iteration.
     */
    @Override
    @Scheduled(cron = "${app.archival.cron:0 0 2 * * ?}")
    public void archiveOldPostings() {
        if (!enabled) {
            log.debug("ARCHIVAL | disabled — skipping");
            return;
        }

        Instant threshold = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);
        log.info("ARCHIVAL JOB START | threshold={} (>{} days)", threshold, thresholdDays);

        int totalPostings = 0;
        int totalLegs = 0;
        int batchNum = 0;

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        int[] batchResult;
        do {
            batchResult = tx.execute(status -> processBatch(threshold));
            if (batchResult == null) break;

            int postingsArchived = batchResult[0];
            int legsArchived = batchResult[1];
            totalPostings += postingsArchived;
            totalLegs += legsArchived;
            batchNum++;
            log.info("ARCHIVAL | batch={} postings={} legs={} totalPostings={} totalLegs={}",
                    batchNum, postingsArchived, legsArchived, totalPostings, totalLegs);

        } while (batchResult[0] == batchSize); // keep going while the batch was full

        log.info("ARCHIVAL JOB COMPLETE | batches={} totalPostings={} totalLegs={}",
                batchNum, totalPostings, totalLegs);
    }

    /**
     * Processes a single batch within a transaction.
     *
     * @return int[]{postingsArchived, legsArchived}
     */
    private int[] processBatch(Instant threshold) {
        List<AccountPostingEntity> batch = postingRepository.findEligibleForArchival(
                threshold, PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            return new int[]{0, 0};
        }

        List<Long> postingIds = batch.stream()
                .map(AccountPostingEntity::getPostingId)
                .toList();

        List<AccountPostingLegEntity> legs = legRepository.findByPostingIdIn(postingIds);

        Instant archivedAt = Instant.now();

        // Insert into history tables
        historyRepository.saveAll(batch.stream()
                .map(p -> toHistory(p, archivedAt))
                .toList());

        legHistoryRepository.saveAll(legs.stream()
                .map(l -> toLegHistory(l, archivedAt))
                .toList());

        // Delete originals — legs first to satisfy FK constraint
        legRepository.deleteAllInBatch(legs);
        postingRepository.deleteAllInBatch(batch);

        return new int[]{batch.size(), legs.size()};
    }

    // ── conversion helpers ────────────────────────────────────────────────────

    private AccountPostingHistoryEntity toHistory(AccountPostingEntity src, Instant archivedAt) {
        return AccountPostingHistoryEntity.builder()
                .postingId(src.getPostingId())
                .sourceReferenceId(src.getSourceReferenceId())
                .endToEndReferenceId(src.getEndToEndReferenceId())
                .sourceName(src.getSourceName())
                .requestType(src.getRequestType())
                .amount(src.getAmount())
                .currency(src.getCurrency())
                .creditDebitIndicator(src.getCreditDebitIndicator())
                .debtorAccount(src.getDebtorAccount())
                .creditorAccount(src.getCreditorAccount())
                .requestedExecutionDate(src.getRequestedExecutionDate())
                .remittanceInformation(src.getRemittanceInformation())
                .status(src.getStatus())
                .requestPayload(src.getRequestPayload())
                .responsePayload(src.getResponsePayload())
                .retryLockedUntil(src.getRetryLockedUntil())
                .targetSystems(src.getTargetSystems())
                .reason(src.getReason())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .archivedAt(archivedAt)
                .build();
    }

    private AccountPostingLegHistoryEntity toLegHistory(AccountPostingLegEntity src, Instant archivedAt) {
        return AccountPostingLegHistoryEntity.builder()
                .postingLegId(src.getPostingLegId())
                .postingId(src.getPostingId())
                .legOrder(src.getLegOrder())
                .targetSystem(src.getTargetSystem())
                .account(src.getAccount())
                .status(src.getStatus())
                .referenceId(src.getReferenceId())
                .reason(src.getReason())
                .attemptNumber(src.getAttemptNumber())
                .postedTime(src.getPostedTime())
                .requestPayload(src.getRequestPayload())
                .responsePayload(src.getResponsePayload())
                .mode(src.getMode())
                .operation(src.getOperation())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .archivedAt(archivedAt)
                .build();
    }
}
