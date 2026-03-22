package com.accountposting.controller;

import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.UpdateLegRequest;
import com.accountposting.entity.enums.LegStatus;
import com.accountposting.service.AccountPostingLegService;
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
@RequestMapping("/account-posting/{postingId}/leg")
@RequiredArgsConstructor
public class AccountPostingLegController {

    private final AccountPostingLegService service;

    @PostMapping
    public ResponseEntity<AccountPostingLegResponse> addLeg(
            @PathVariable Long postingId,
            @Valid @RequestBody AccountPostingLegRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addLeg(postingId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountPostingLegResponse>> listLegs(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.listLegs(postingId));
    }

    @GetMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponse> getLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId) {
        return ResponseEntity.ok(service.getLeg(postingId, postingLegId));
    }

    @PutMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponse> updateLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId,
            @Valid @RequestBody UpdateLegRequest request) {
        return ResponseEntity.ok(service.updateLeg(postingId, postingLegId, request));
    }

    /**
     * Manual UI status update — sets mode=MANUAL, does not increment attempt count.
     */
    @PatchMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponse> manualUpdateLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId,
            @Valid @RequestBody ManualUpdateRequest request) {
        return ResponseEntity.ok(service.manualUpdateLeg(postingId, postingLegId, request.getStatus()));
    }

    @Data
    static class ManualUpdateRequest {
        @NotNull(message = "status is required")
        private LegStatus status;
    }
}
