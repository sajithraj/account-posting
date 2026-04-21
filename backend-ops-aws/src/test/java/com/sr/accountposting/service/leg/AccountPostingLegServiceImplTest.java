package com.sr.accountposting.service.leg;

import com.sr.accountposting.dto.leg.LegResponse;
import com.sr.accountposting.entity.leg.AccountPostingLegEntity;
import com.sr.accountposting.exception.ResourceNotFoundException;
import com.sr.accountposting.repository.leg.AccountPostingLegRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountPostingLegServiceImplTest {

    @Mock
    private AccountPostingLegRepository legRepo;

    private AccountPostingLegServiceImpl service;

    private static final Long POSTING_ID = 100001L;

    @BeforeEach
    void setUp() {
        service = new AccountPostingLegServiceImpl(legRepo);
    }

    // ─── listLegs ─────────────────────────────────────────────────────────────

    @Test
    void listLegs_multipleLegs_returnsAllMappedToResponse() {
        AccountPostingLegEntity cbs = buildLeg(POSTING_ID, 1, "CBS", "ACSP");
        AccountPostingLegEntity gl = buildLeg(POSTING_ID, 2, "GL", "ACSP");
        AccountPostingLegEntity obpm = buildLeg(POSTING_ID, 3, "OBPM", "FAILED");
        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of(cbs, gl, obpm));

        List<LegResponse> legs = service.listLegs(POSTING_ID);

        assertThat(legs).hasSize(3);
        assertThat(legs).extracting(LegResponse::getTargetSystem)
                .containsExactly("CBS", "GL", "OBPM");
        assertThat(legs).extracting(LegResponse::getStatus)
                .containsExactly("ACSP", "ACSP", "FAILED");
    }

    @Test
    void listLegs_noLegs_returnsEmptyList() {
        when(legRepo.findByPostingId(anyLong())).thenReturn(List.of());

        List<LegResponse> legs = service.listLegs(POSTING_ID);

        assertThat(legs).isEmpty();
    }

    @Test
    void listLegs_responseIncludesAllFields() {
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "CBS", "ACSP");
        leg.setReferenceId("CBS-TXN-001");
        leg.setReason(null);
        leg.setAttemptNumber(2);
        leg.setPostedTime("2026-04-21T10:00:00Z");
        leg.setMode("RETRY");
        leg.setOperation("POSTING");
        leg.setCreatedAt("2026-04-21T09:00:00Z");
        leg.setUpdatedAt("2026-04-21T10:00:00Z");

        when(legRepo.findByPostingId(POSTING_ID)).thenReturn(List.of(leg));

        LegResponse response = service.listLegs(POSTING_ID).get(0);

        assertThat(response.getPostingId()).isEqualTo(POSTING_ID);
        assertThat(response.getTransactionOrder()).isEqualTo(1);
        assertThat(response.getReferenceId()).isEqualTo("CBS-TXN-001");
        assertThat(response.getAttemptNumber()).isEqualTo(2);
        assertThat(response.getPostedTime()).isEqualTo("2026-04-21T10:00:00Z");
        assertThat(response.getMode()).isEqualTo("RETRY");
        assertThat(response.getOperation()).isEqualTo("POSTING");
    }

    // ─── getLeg ───────────────────────────────────────────────────────────────

    @Test
    void getLeg_existingLeg_returnsMappedResponse() {
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "GL", "ACSP");
        leg.setReferenceId("GL-TXN-001");
        when(legRepo.findByPostingIdAndOrder(POSTING_ID, 1)).thenReturn(Optional.of(leg));

        LegResponse response = service.getLeg(POSTING_ID, 1);

        assertThat(response.getPostingId()).isEqualTo(POSTING_ID);
        assertThat(response.getTransactionOrder()).isEqualTo(1);
        assertThat(response.getTargetSystem()).isEqualTo("GL");
        assertThat(response.getStatus()).isEqualTo("ACSP");
        assertThat(response.getReferenceId()).isEqualTo("GL-TXN-001");
    }

    @Test
    void getLeg_notFound_throwsResourceNotFoundException() {
        when(legRepo.findByPostingIdAndOrder(anyLong(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLeg(POSTING_ID, 99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(POSTING_ID))
                .hasMessageContaining("99");
    }

    // ─── manualUpdateLeg ──────────────────────────────────────────────────────

    @Test
    void manualUpdateLeg_updatesStatusReasonModeAndRequestedBy() {
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "CBS", "FAILED");
        leg.setUpdatedBy("SYSTEM");
        when(legRepo.findByPostingIdAndOrder(POSTING_ID, 1)).thenReturn(Optional.of(leg));

        service.manualUpdateLeg(POSTING_ID, 1, "SUCCESS", "Manually resolved", "ops-admin");

        ArgumentCaptor<AccountPostingLegEntity> captor = ArgumentCaptor.forClass(AccountPostingLegEntity.class);
        verify(legRepo).update(captor.capture());

        AccountPostingLegEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getReason()).isEqualTo("Manually resolved");
        assertThat(saved.getMode()).isEqualTo("MANUAL");
        assertThat(saved.getUpdatedBy()).isEqualTo("ops-admin");
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void manualUpdateLeg_doesNotIncrementAttemptNumber() {
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "CBS", "FAILED");
        leg.setAttemptNumber(3);
        when(legRepo.findByPostingIdAndOrder(POSTING_ID, 1)).thenReturn(Optional.of(leg));

        service.manualUpdateLeg(POSTING_ID, 1, "SUCCESS", "Manual fix", "admin");

        ArgumentCaptor<AccountPostingLegEntity> captor = ArgumentCaptor.forClass(AccountPostingLegEntity.class);
        verify(legRepo).update(captor.capture());

        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(3); // unchanged
    }

    @Test
    void manualUpdateLeg_legNotFound_throwsResourceNotFoundException() {
        when(legRepo.findByPostingIdAndOrder(anyLong(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.manualUpdateLeg(POSTING_ID, 99, "SUCCESS", "reason", "admin"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(String.valueOf(POSTING_ID))
                .hasMessageContaining("99");
    }

    @Test
    void manualUpdateLeg_preservesUnchangedFields() {
        AccountPostingLegEntity leg = buildLeg(POSTING_ID, 1, "CBS", "FAILED");
        leg.setReferenceId("CBS-TXN-ORIGINAL");
        leg.setPostedTime("2026-04-21T08:00:00Z");
        when(legRepo.findByPostingIdAndOrder(POSTING_ID, 1)).thenReturn(Optional.of(leg));

        service.manualUpdateLeg(POSTING_ID, 1, "SUCCESS", "Manually resolved", "ops-admin");

        ArgumentCaptor<AccountPostingLegEntity> captor = ArgumentCaptor.forClass(AccountPostingLegEntity.class);
        verify(legRepo).update(captor.capture());

        // Fields not touched by manualUpdate should remain
        assertThat(captor.getValue().getReferenceId()).isEqualTo("CBS-TXN-ORIGINAL");
        assertThat(captor.getValue().getPostedTime()).isEqualTo("2026-04-21T08:00:00Z");
        assertThat(captor.getValue().getTargetSystem()).isEqualTo("CBS");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AccountPostingLegEntity buildLeg(Long postingId, int order, String targetSystem, String status) {
        AccountPostingLegEntity leg = new AccountPostingLegEntity();
        leg.setPostingId(postingId);
        leg.setTransactionOrder(order);
        leg.setTargetSystem(targetSystem);
        leg.setStatus(status);
        leg.setAttemptNumber(1);
        leg.setOperation("POSTING");
        leg.setMode("NORM");
        leg.setCreatedAt("2026-04-21T09:00:00Z");
        leg.setUpdatedAt("2026-04-21T09:00:00Z");
        return leg;
    }
}
