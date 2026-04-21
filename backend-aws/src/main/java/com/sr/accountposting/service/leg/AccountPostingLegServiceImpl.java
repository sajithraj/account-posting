package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import com.sr.accountposting.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class AccountPostingLegServiceImpl implements AccountPostingLegService {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingLegServiceImpl.class);

    private final AccountPostingLegRepository legRepo;

    @Inject
    public AccountPostingLegServiceImpl(AccountPostingLegRepository legRepo) {
        this.legRepo = legRepo;
    }

    @Override
    public AccountPostingLegEntity createLeg(String postingId, int transactionOrder, String targetSystem,
                                             String account, String operation, String mode, int ttlDays) {
        log.info("createLeg postingId={} order={} targetSystem={} account={} operation={} mode={}",
                postingId, transactionOrder, targetSystem, account, operation, mode);
        String now = Instant.now().toString();
        AccountPostingLegEntity leg = new AccountPostingLegEntity();
        leg.setPostingId(postingId);
        leg.setTransactionOrder(transactionOrder);
        leg.setTargetSystem(targetSystem);
        leg.setAccount(account);
        leg.setStatus("PNDG");
        leg.setAttemptNumber(1);
        leg.setMode(mode);
        leg.setOperation(operation);
        leg.setCreatedAt(now);
        leg.setUpdatedAt(now);
        leg.setCreatedBy("SYSTEM");
        leg.setUpdatedBy("SYSTEM");
        leg.setTtl(IdGenerator.ttlEpochSeconds(ttlDays));
        legRepo.save(leg);
        log.info("createLeg saved postingId={} order={} status=PNDG", postingId, transactionOrder);
        return leg;
    }

    @Override
    public void updateLeg(String postingId, int transactionOrder, String status,
                          String referenceId, String postedTime, String reason,
                          String requestPayload, String responsePayload, boolean isRetry) {
        log.info("updateLeg postingId={} order={} status={} referenceId={} isRetry={}",
                postingId, transactionOrder, status, referenceId, isRetry);
        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found postingId=" + postingId + " order=" + transactionOrder));

        leg.setStatus(status);
        leg.setReferenceId(referenceId);
        leg.setPostedTime(postedTime);
        leg.setReason(reason);
        leg.setRequestPayload(requestPayload);
        leg.setResponsePayload(responsePayload);
        leg.setUpdatedAt(Instant.now().toString());
        leg.setUpdatedBy("SYSTEM");

        if (isRetry) {
            leg.setAttemptNumber(leg.getAttemptNumber() == null ? 2 : leg.getAttemptNumber() + 1);
            leg.setMode("RETRY");
        }

        legRepo.update(leg);
        log.info("updateLeg done postingId={} order={} status={} attemptNumber={}",
                postingId, transactionOrder, status, leg.getAttemptNumber());
    }

    @Override
    public void manualUpdateLeg(String postingId, int transactionOrder,
                                String status, String reason, String requestedBy) {
        log.info("manualUpdateLeg postingId={} order={} status={} requestedBy={}",
                postingId, transactionOrder, status, requestedBy);
        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found postingId=" + postingId + " order=" + transactionOrder));

        leg.setStatus(status);
        leg.setReason(reason);
        leg.setMode("MANUAL");
        leg.setUpdatedAt(Instant.now().toString());
        leg.setUpdatedBy(requestedBy);
        legRepo.update(leg);
        log.info("manualUpdateLeg done postingId={} order={} status={}", postingId, transactionOrder, status);
    }

    @Override
    public List<LegResponse> listLegs(String postingId) {
        log.info("listLegs postingId={}", postingId);
        List<LegResponse> legs = legRepo.findByPostingId(postingId).stream()
                .map(this::toResponse).collect(Collectors.toList());
        log.info("listLegs postingId={} count={}", postingId, legs.size());
        return legs;
    }

    @Override
    public LegResponse getLeg(String postingId, int transactionOrder) {
        log.info("getLeg postingId={} order={}", postingId, transactionOrder);
        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found postingId=" + postingId + " order=" + transactionOrder));
        return toResponse(leg);
    }

    @Override
    public List<AccountPostingLegEntity> listNonSuccessLegs(String postingId) {
        return legRepo.findNonSuccessByPostingId(postingId);
    }

    private LegResponse toResponse(AccountPostingLegEntity leg) {
        return LegResponse.builder()
                .postingId(leg.getPostingId())
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
