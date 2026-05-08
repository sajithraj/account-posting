package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class AccountPostingLegServiceImpl implements AccountPostingLegService {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingLegServiceImpl.class);

    private final AccountPostingLegRepository legRepo;

    @Inject
    public AccountPostingLegServiceImpl(AccountPostingLegRepository legRepo) {
        this.legRepo = legRepo;
    }

    @Override
    public void manualUpdateLeg(String postingId, Integer transactionOrder,
                                String status, String reason, String requestedBy) {
        log.info("Manual leg update started | postingId={} transactionOrder={} newStatus={} requestedBy={}",
                postingId, transactionOrder, status, requestedBy);

        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> {
                    log.warn("Leg not found | postingId={} transactionOrder={}", postingId, transactionOrder);
                    return new ResourceNotFoundException(
                            "Leg not found: postingId=" + postingId + " transactionOrder=" + transactionOrder);
                });

        String previousStatus = leg.getStatus();
        leg.setStatus(status);
        leg.setReason(reason);
        leg.setMode("MANUAL");
        leg.setUpdatedAt(Instant.now().toString());
        leg.setUpdatedBy(requestedBy);
        legRepo.update(leg);

        log.info("Manual leg update completed | postingId={} transactionOrder={} transactionId={} previousStatus={} newStatus={} updatedBy={}",
                postingId, transactionOrder, leg.getTransactionId(), previousStatus, status, requestedBy);
    }

    @Override
    public LegResponse getLeg(String postingId, Integer transactionOrder) {
        log.info("Get leg | postingId={} transactionOrder={}", postingId, transactionOrder);

        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> {
                    log.warn("Leg not found | postingId={} transactionOrder={}", postingId, transactionOrder);
                    return new ResourceNotFoundException(
                            "Leg not found: postingId=" + postingId + " transactionOrder=" + transactionOrder);
                });

        LegResponse response = toResponse(leg);
        log.info("Get leg completed | postingId={} transactionOrder={} transactionId={} status={} targetSystem={}",
                postingId, transactionOrder, response.getTransactionId(),
                response.getStatus(), response.getTargetSystem());
        return response;
    }

    private LegResponse toResponse(AccountPostingLegEntity leg) {
        return LegResponse.builder()
                .postingId(leg.getPostingId())
                .transactionId(leg.getTransactionId())
                .transactionOrder(leg.getTransactionOrder())
                .targetSystem(leg.getTargetSystem())
                .account(leg.getAccount())
                .status(leg.getStatus())
                .referenceId(leg.getReferenceId())
                .reason(leg.getReason())
                .attemptNumber(leg.getAttemptNumber())
                .postedTime(leg.getPostedTime())
                .mode(leg.getMode())
                .operation(leg.getOperation())
                .createdAt(leg.getCreatedAt())
                .updatedAt(leg.getUpdatedAt())
                .build();
    }
}
