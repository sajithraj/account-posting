package com.sajith.payments.redesign.controller;

import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.ManualUpdateRequestV2;
import com.sajith.payments.redesign.service.accountpostingleg.AccountPostingLegServiceV2;
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
@RequestMapping("/v2/payment/account-posting/{postingId}/transaction")
@RequiredArgsConstructor
public class AccountPostingLegControllerV2 {

    private final AccountPostingLegServiceV2 service;

    @GetMapping
    public ResponseEntity<List<AccountPostingLegResponseV2>> listTransactions(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.listLegs(postingId));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<AccountPostingLegResponseV2> getTransaction(
            @PathVariable Long postingId,
            @PathVariable Long transactionId) {
        return ResponseEntity.ok(service.getLeg(postingId, transactionId));
    }

    @PatchMapping("/{transactionId}")
    public ResponseEntity<AccountPostingLegResponseV2> manualUpdateTransaction(
            @PathVariable Long postingId,
            @PathVariable Long transactionId,
            @Valid @RequestBody ManualUpdateRequestV2 request) {
        return ResponseEntity.ok(service.manualUpdateLeg(postingId, transactionId, request));
    }
}
