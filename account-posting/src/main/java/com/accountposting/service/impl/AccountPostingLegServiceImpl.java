package com.accountposting.service.impl;

import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.UpdateLegRequest;
import com.accountposting.entity.AccountPostingLeg;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.AccountPostingLegMapper;
import com.accountposting.repository.AccountPostingLegRepository;
import com.accountposting.repository.AccountPostingRepository;
import com.accountposting.service.AccountPostingLegService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPostingLegServiceImpl implements AccountPostingLegService {

    private final AccountPostingLegRepository repository;
    private final AccountPostingRepository postingRepository;
    private final AccountPostingLegMapper mapper;

    @Override
    @Transactional
    public AccountPostingLegResponse addLeg(Long postingId, AccountPostingLegRequest request) {
        AccountPostingLeg leg = mapper.toEntity(request);
        leg.setPostingId(postingId);
        AccountPostingLeg saved = repository.save(leg);
        log.info("Added leg postingLegId={} postingId={} targetSystem={} status={}",
                saved.getPostingLegId(), postingId, saved.getTargetSystem(), saved.getStatus());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPostingLegResponse> listLegs(Long postingId) {
        return repository.findByPostingIdOrderByLegOrder(postingId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingLegResponse getLeg(Long postingId, Long postingLegId) {
        return mapper.toResponse(getOrThrow(postingId, postingLegId));
    }

    @Override
    @Transactional
    public AccountPostingLegResponse updateLeg(Long postingId, Long postingLegId,
                                               UpdateLegRequest request) {
        log.debug("Updating leg postingLegId={} postingId={} newStatus={} mode={}",
                postingLegId, postingId, request.getStatus(), request.getMode());
        AccountPostingLeg leg = getOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        // Only increment attempt count for retry path — MANUAL updates do not count as attempts
        if (request.getMode() == LegMode.RETRY) {
            leg.setAttemptNumber(leg.getAttemptNumber() + 1);
        }
        mapper.applyUpdate(request, leg);
        AccountPostingLeg updated = repository.save(leg);
        log.info("Updated leg postingLegId={} postingId={} status: {} -> {} mode={} attempt={}",
                postingLegId, postingId, previousStatus, updated.getStatus(),
                updated.getMode(), updated.getAttemptNumber());
        return mapper.toResponse(updated);
    }

    @Override
    @Transactional
    public AccountPostingLegResponse manualUpdateLeg(Long postingId, Long postingLegId,
                                                     LegStatus newStatus) {
        log.info("Manual update leg postingLegId={} postingId={} newStatus={}",
                postingLegId, postingId, newStatus);
        AccountPostingLeg leg = getOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        leg.setStatus(newStatus);
        leg.setMode(LegMode.MANUAL);
        AccountPostingLeg updated = repository.save(leg);
        log.info("Manually updated leg postingLegId={} postingId={} status: {} -> {}",
                postingLegId, postingId, previousStatus, updated.getStatus());

        // Promote posting to SUCCESS when every leg is now SUCCESS
        if (newStatus == LegStatus.SUCCESS) {
            boolean allSuccess = repository.findNonSuccessByPostingId(postingId, LegStatus.SUCCESS).isEmpty();
            if (allSuccess) {
                postingRepository.findById(postingId).ifPresent(posting -> {
                    posting.setStatus(PostingStatus.SUCCESS);
                    posting.setReason("Request processed successfully");
                    postingRepository.save(posting);
                    log.info("All legs SUCCESS — promoted postingId={} to SUCCESS", postingId);
                });
            }
        }

        return mapper.toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPostingLegResponse> listNonSuccessLegs(Long postingId) {
        return repository.findNonSuccessByPostingId(postingId, LegStatus.SUCCESS)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private AccountPostingLeg getOrThrow(Long postingId, Long postingLegId) {
        return repository.findByPostingLegIdAndPostingId(postingLegId, postingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AccountPostingLeg", postingLegId + " under posting " + postingId));
    }
}
