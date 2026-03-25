package com.accountposting.controller;

import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.UpdateLegRequestV2;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/payment/account-posting/{postingId}/leg")
@RequiredArgsConstructor
public class AccountPostingLegControllerV2 {

    private final AccountPostingLegServiceV2 service;

    @PostMapping
    public ResponseEntity<AccountPostingLegResponseV2> addLeg(
            @PathVariable Long postingId,
            @Valid @RequestBody AccountPostingLegRequestV2 request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLeg(postingId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountPostingLegResponseV2>> listLegs(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.listLegs(postingId));
    }

    @GetMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponseV2> getLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId) {
        return ResponseEntity.ok(service.getLeg(postingId, postingLegId));
    }

    @PutMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponseV2> updateLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId,
            @Valid @RequestBody UpdateLegRequestV2 request) {
        return ResponseEntity.ok(service.updateLeg(postingId, postingLegId, request));
    }

    /**
     * Manual UI status update — sets mode=MANUAL, does not increment attempt count.
     * Accepts an optional reason so the UI can persist the failure/override explanation.
     */
    @PatchMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponseV2> manualUpdateLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId,
            @Valid @RequestBody ManualUpdateRequest request) {
        return ResponseEntity.ok(service.manualUpdateLeg(postingId, postingLegId, request.getStatus(), request.getReason()));
    }

    @Data
    static class ManualUpdateRequest {
        @NotNull(message = "status is required")
        private LegStatus status;
        private String reason;
    }
}
