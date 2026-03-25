package com.accountposting.service.accountpostingleg;

import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.UpdateLegRequestV2;
import com.accountposting.entity.AccountPostingLegEntity;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.entity.enums.PostingStatus;
import com.accountposting.exception.ResourceNotFoundException;
import com.accountposting.mapper.AccountPostingLegMapperV2;
import com.accountposting.mapper.MappingUtilsV2;
import com.accountposting.repository.AccountPostingLegRepository;
import com.accountposting.repository.AccountPostingRepository;
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
    private final MappingUtilsV2 mappingUtils;

    @Override
    @Transactional
    public AccountPostingLegResponseV2 addLeg(Long postingId, AccountPostingLegRequestV2 request) {
        log.info("ADD LEG REQUEST | postingId={} {}", postingId, mappingUtils.toJson(request));
        AccountPostingLegEntity leg = mapper.toEntity(request);
        leg.setPostingId(postingId);
        log.info("Persisting leg | postingId={} targetSystem={} legOrder={} operation={} mode={} status={}",
                postingId, leg.getTargetSystem(), leg.getLegOrder(),
                leg.getOperation(), leg.getMode(), leg.getStatus());
        AccountPostingLegEntity saved = repository.save(leg);
        AccountPostingLegResponseV2 addLegResponse = mapper.toResponse(saved);
        log.info("ADD LEG RESPONSE | postingId={} {}", postingId, mappingUtils.toJson(addLegResponse));
        return addLegResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountPostingLegResponseV2> listLegs(Long postingId) {
        log.info("LIST LEGS REQUEST | postingId={}", postingId);
        List<AccountPostingLegResponseV2> legs = repository.findByPostingIdOrderByLegOrder(postingId)
                .stream()
                .map(mapper::toResponse)
                .toList();
        log.info("LIST LEGS RESPONSE | postingId={} {}", postingId, mappingUtils.toJson(legs));
        return legs;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPostingLegResponseV2 getLeg(Long postingId, Long postingLegId) {
        log.info("GET LEG REQUEST | postingId={} postingLegId={}", postingId, postingLegId);
        AccountPostingLegResponseV2 response = mapper.toResponse(getOrThrow(postingId, postingLegId));
        log.info("GET LEG RESPONSE | postingId={} postingLegId={} {}", postingId, postingLegId, mappingUtils.toJson(response));
        return response;
    }

    @Override
    @Transactional
    public AccountPostingLegResponseV2 updateLeg(Long postingId, Long postingLegId,
                                                 UpdateLegRequestV2 request) {
        log.info("UPDATE LEG REQUEST | postingId={} postingLegId={} {}", postingId, postingLegId, mappingUtils.toJson(request));
        AccountPostingLegEntity leg = getOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        // Only increment attempt count for retry path - MANUAL updates do not count as attempts
        if (request.getMode() == LegMode.RETRY) {
            leg.setAttemptNumber(leg.getAttemptNumber() + 1);
        }
        mapper.applyUpdate(request, leg);
        log.info("Persisting leg update | postingLegId={} postingId={} status={} mode={} attempt={}",
                postingLegId, postingId, leg.getStatus(), leg.getMode(), leg.getAttemptNumber());
        AccountPostingLegEntity updated = repository.save(leg);
        AccountPostingLegResponseV2 updateLegResponse = mapper.toResponse(updated);
        log.info("UPDATE LEG RESPONSE | postingId={} postingLegId={} previousStatus={} {}",
                postingId, postingLegId, previousStatus, mappingUtils.toJson(updateLegResponse));
        return updateLegResponse;
    }

    @Override
    @Transactional
    public AccountPostingLegResponseV2 manualUpdateLeg(Long postingId, Long postingLegId,
                                                       LegStatus newStatus, String reason) {
        log.info("MANUAL UPDATE LEG REQUEST | postingId={} postingLegId={} newStatus={} reason={}",
                postingId, postingLegId, newStatus, reason);
        AccountPostingLegEntity leg = getOrThrow(postingId, postingLegId);
        LegStatus previousStatus = leg.getStatus();
        leg.setStatus(newStatus);
        leg.setMode(LegMode.MANUAL);
        if (reason != null && !reason.isBlank()) {
            leg.setReason(reason);
        }
        log.info("Persisting manual leg update | postingLegId={} postingId={} status={} mode={}",
                postingLegId, postingId, leg.getStatus(), leg.getMode());
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
        log.info("MANUAL UPDATE LEG RESPONSE | postingId={} postingLegId={} previousStatus={} {}",
                postingId, postingLegId, previousStatus, mappingUtils.toJson(manualUpdateResponse));
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

    private AccountPostingLegEntity getOrThrow(Long postingId, Long postingLegId) {
        return repository.findByPostingLegIdAndPostingId(postingLegId, postingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AccountPostingLegEntity", postingLegId + " under posting " + postingId));
    }
}
