package com.accountposting.controller;

import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.ManualUpdateRequestV2;
import com.accountposting.service.accountpostingleg.AccountPostingLegServiceV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/payment/account-posting/{postingId}/leg")
@RequiredArgsConstructor
public class AccountPostingLegControllerV2 {

    private final AccountPostingLegServiceV2 service;

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

    @PatchMapping("/{postingLegId}")
    public ResponseEntity<AccountPostingLegResponseV2> manualUpdateLeg(
            @PathVariable Long postingId,
            @PathVariable Long postingLegId,
            @Valid @RequestBody ManualUpdateRequestV2 request) {
        return ResponseEntity.ok(service.manualUpdateLeg(postingId, postingLegId, request.getStatus(), request.getReason()));
    }
}
