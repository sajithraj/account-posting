package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;

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
    public void manualUpdateLeg(String postingId, String transactionId,
                                String status, String reason, String requestedBy) {
        AccountPostingLegEntity leg = legRepo.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found transactionId=" + transactionId));
        if (!postingId.equals(leg.getPostingId())) {
            throw new ResourceNotFoundException(
                    "Leg transactionId=" + transactionId + " does not belong to postingId=" + postingId);
        }
        leg.setStatus(status);
        leg.setReason(reason);
        leg.setMode("MANUAL");
        leg.setUpdatedAt(Instant.now().toString());
        leg.setUpdatedBy(requestedBy);
        legRepo.update(leg);
    }

    @Override
    public List<LegResponse> listLegs(String postingId) {
        return legRepo.findByPostingId(postingId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public LegResponse getLeg(String postingId, String transactionId) {
        AccountPostingLegEntity leg = legRepo.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg not found transactionId=" + transactionId));
        if (!postingId.equals(leg.getPostingId())) {
            throw new ResourceNotFoundException(
                    "Leg transactionId=" + transactionId + " does not belong to postingId=" + postingId);
        }
        return toResponse(leg);
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
