package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import com.sr.accountposting.util.IdGenerator;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class AccountPostingLegServiceImpl implements AccountPostingLegService {

    private final AccountPostingLegRepository legRepo;

    @Inject
    public AccountPostingLegServiceImpl(AccountPostingLegRepository legRepo) {
        this.legRepo = legRepo;
    }

    @Override
    public AccountPostingLegEntity createLeg(Long postingId, int transactionOrder, String targetSystem,
                                             String account, String operation, String mode, int ttlDays) {
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
        return leg;
    }

    @Override
    public void updateLeg(Long postingId, int transactionOrder, String status,
                          String referenceId, String postedTime, String reason,
                          String requestPayload, String responsePayload, boolean isRetry) {
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
    }

    @Override
    public void manualUpdateLeg(Long postingId, int transactionOrder,
                                String status, String reason, String requestedBy) {
        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found postingId=" + postingId + " order=" + transactionOrder));

        leg.setStatus(status);
        leg.setReason(reason);
        leg.setMode("MANUAL");
        leg.setUpdatedAt(Instant.now().toString());
        leg.setUpdatedBy(requestedBy);
        // attemptNumber NOT incremented for MANUAL override
        legRepo.update(leg);
    }

    @Override
    public List<LegResponse> listLegs(Long postingId) {
        return legRepo.findByPostingId(postingId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public LegResponse getLeg(Long postingId, int transactionOrder) {
        AccountPostingLegEntity leg = legRepo.findByPostingIdAndOrder(postingId, transactionOrder)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found postingId=" + postingId + " order=" + transactionOrder));
        return toResponse(leg);
    }

    @Override
    public List<AccountPostingLegEntity> listNonSuccessLegs(Long postingId) {
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
