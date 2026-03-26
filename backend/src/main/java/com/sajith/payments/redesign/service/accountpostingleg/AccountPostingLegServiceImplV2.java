package com.sajith.payments.redesign.service.accountpostingleg;

import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.UpdateLegRequestV2;
import com.sajith.payments.redesign.entity.AccountPostingLegEntity;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import com.sajith.payments.redesign.exception.ResourceNotFoundException;
import com.sajith.payments.redesign.mapper.AccountPostingLegMapperV2;
import com.sajith.payments.redesign.repository.AccountPostingLegRepository;
import com.sajith.payments.redesign.repository.AccountPostingRepository;
import com.sajith.payments.redesign.utils.AppUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPostingLegServiceImplV2 implements AccountPostingLegServiceV2 {

    private final AccountPostingLegRepository repository;
    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegMapperV2 mapper;
    private final AppUtility appUtility;

    @Override
    @Transactional
    public AccountPostingLegResponseV2 addLeg(Long postingId, AccountPostingLegRequestV2 request) {
        log.info("Request received to add leg for posting id :: {}. Received request :: {} .", postingId, appUtility.toObjectToString(request));
        AccountPostingLegEntity leg = mapper.toEntity(request);
        leg.setPostingId(postingId);
        log.info("Persisting leg data for postingId :: {} target system :: {} leg order :: {} operation :: {} mode :: {} status :: {}",
                postingId, leg.getTargetSystem(), leg.getLegOrder(),
                leg.getOperation(), leg.getMode(), leg.getStatus());
        AccountPostingLegEntity saved = repository.save(leg);
        AccountPostingLegResponseV2 addLegResponse = mapper.toResponse(saved);
        log.info("Leg persisted successfully for posting id :: {}. Persisted leg :: {} .", postingId, appUtility.toObjectToString(addLegResponse));
        return addLegResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPostingLegResponseV2> listLegs(Long postingId) {
        log.info("Request received to fetch legs for posting id :: {} .", postingId);
        List<AccountPostingLegResponseV2> legs = repository.findByPostingIdOrderByLegOrder(postingId)
                .stream()
                .map(mapper::toResponse)
                .toList();
        log.info("Fetch legs response for the posting id :: {} . Leg details :: {} .", postingId, appUtility.toObjectToString(legs));
        return legs;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingLegResponseV2 getLeg(Long postingId, Long postingLegId) {
        log.info("Request received to fetch leg for posting id :: {} and posting leg id :: {} .", postingId, postingLegId);
        AccountPostingLegResponseV2 response = mapper.toResponse(getLegByPostingIdAndPostingLegIdOrThrow(postingId, postingLegId));
        log.info("Fetch leg response for the posting id :: {} and posting leg id :: {} . Leg detail :: {} .", postingId, postingLegId, appUtility.toObjectToString(response));
        return response;
    }

    @Override
    @Transactional
    public AccountPostingLegResponseV2 updateLeg(Long postingId, Long postingLegId, UpdateLegRequestV2 request) {
        log.info("Request received to update leg for posting id :: {} and posting leg id :: {} . Received request :: {} .", postingId, postingLegId, appUtility.toObjectToString(request));
        AccountPostingLegEntity leg = getLegByPostingIdAndPostingLegIdOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        // Only increment attempt count for retry path - MANUAL updates do not count as attempts
        if (request.getMode() == LegMode.RETRY) {
            leg.setAttemptNumber(leg.getAttemptNumber() + 1);
        }
        mapper.applyUpdate(request, leg);
        log.info("Persisting leg update for posting leg id :: {} posting id :: {} status :: {} mode :: {} attempt :: {} .", postingLegId, postingId, leg.getStatus(), leg.getMode(), leg.getAttemptNumber());
        AccountPostingLegEntity updated = repository.save(leg);
        AccountPostingLegResponseV2 updateLegResponse = mapper.toResponse(updated);
        log.info("Update leg status from for the posting id :: {} , posting leg id :: {} and previous status :: {} . Updated leg detail :: {} .", postingId, postingLegId, previousStatus, appUtility.toObjectToString(updateLegResponse));
        return updateLegResponse;
    }

    @Override
    @Transactional
    public AccountPostingLegResponseV2 manualUpdateLeg(Long postingId, Long postingLegId, LegStatus newStatus, String reason) {
        log.info("Request received to update leg manually for posting id :: {} and posting leg id :: {} . Update details - new status :: {} and reason :: {} .", postingId, postingLegId, newStatus, reason);
        AccountPostingLegEntity leg = getLegByPostingIdAndPostingLegIdOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        leg.setStatus(newStatus);
        leg.setMode(LegMode.MANUAL);
        if (reason != null && !reason.isBlank()) {
            leg.setReason(reason);
        }
        log.info("Persisting manual leg update for posting leg id :: {} posting id :: {} . Status updated from :: {}  to status :: {} .", postingLegId, postingId, previousStatus, newStatus);
        AccountPostingLegEntity updated = repository.save(leg);

        // Promote posting to SUCCESS when every leg is now SUCCESS
        if (newStatus == LegStatus.SUCCESS) {
            boolean allSuccess = repository.findNonSuccessByPostingId(postingId, LegStatus.SUCCESS).isEmpty();
            if (allSuccess) {
                postingRepository.findById(postingId).ifPresent(posting -> {
                    posting.setStatus(PostingStatus.ACSP);
                    posting.setReason("Request processed successfully");
                    postingRepository.save(posting);
                    log.info("All legs SUCCESS - promoted postingId={} to ACSP", postingId);
                });
            }
        }

        AccountPostingLegResponseV2 manualUpdateResponse = mapper.toResponse(updated);
        log.info("Manual leg updated completed successfully for posting leg id :: {} posting id :: {}. Updated leg details :: {} .", postingId, postingLegId, appUtility.toObjectToString(manualUpdateResponse));
        return manualUpdateResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPostingLegResponseV2> listNonSuccessLegs(Long postingId) {
        return repository.findNonSuccessByPostingId(postingId, LegStatus.SUCCESS)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private AccountPostingLegEntity getLegByPostingIdAndPostingLegIdOrThrow(Long postingId, Long postingLegId) {
        return repository.findByPostingLegIdAndPostingId(postingLegId, postingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AccountPostingLegEntity", postingLegId + " under posting " + postingId));
    }
}
