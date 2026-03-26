package com.sajith.payments.redesign.service.archival;

import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.AccountPostingLegEntity;
import com.sajith.payments.redesign.mapper.AccountPostingLegMapperV2;
import com.sajith.payments.redesign.mapper.AccountPostingMapperV2;
import com.sajith.payments.redesign.repository.AccountPostingHistoryRepository;
import com.sajith.payments.redesign.repository.AccountPostingLegHistoryRepository;
import com.sajith.payments.redesign.repository.AccountPostingLegRepository;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
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
 * Scheduled archival job - 90days old data.
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
    private final AccountPostingMapperV2 mapper;
    private final AccountPostingLegMapperV2 legMapper;

    @Value("${app.archival.enabled}")
    private boolean enabled;

    @Value("${app.archival.threshold-days:90}")
    private int thresholdDays;

    @Value("${app.archival.batch-size:500}")
    private int batchSize;

    /**
     * Entry point called by Spring Scheduler.
     */
    @Override
    @Scheduled(cron = "${app.archival.cron:0 0 2 * * ?}")
    public void archiveOldPostings() {
        if (!enabled) {
            log.debug("Archival disabled so skipping");
            return;
        }

        Instant threshold = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);
        log.info("Archival job started to archive last {} days data in batches.", thresholdDays);

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
            log.info("Archival batch :: {} postings :: {} legs :: {} total postings :: {} total legs  {}", batchNum, postingsArchived, legsArchived, totalPostings, totalLegs);

        } while (batchResult[0] == batchSize); // keep going while the batch was full

        log.info("Archival job completed. Batches :: {} total postings :: {} totalLegs :: {} .", batchNum, totalPostings, totalLegs);
    }

    /**
     * Processes a single batch within a transaction.
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
                .map(p -> mapper.toHistory(p, archivedAt))
                .toList());

        legHistoryRepository.saveAll(legs.stream()
                .map(l -> legMapper.toLegHistory(l, archivedAt))
                .toList());

        // Delete originals - legs first to satisfy FK constraint
        legRepository.deleteAllInBatch(legs);
        postingRepository.deleteAllInBatch(batch);

        return new int[]{batch.size(), legs.size()};
    }

}
