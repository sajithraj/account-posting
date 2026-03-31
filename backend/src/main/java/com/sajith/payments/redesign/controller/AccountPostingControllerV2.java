package com.sajith.payments.redesign.controller;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.retry.RetryRequestV2;
import com.sajith.payments.redesign.dto.retry.RetryResponseV2;
import com.sajith.payments.redesign.dto.search.PostingSearchRequestV2;
import com.sajith.payments.redesign.dto.search.PostingSearchResponseV2;
import com.sajith.payments.redesign.service.accountposting.AccountPostingServiceV2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/payment/account-posting")
@RequiredArgsConstructor
public class AccountPostingControllerV2 {

    private final AccountPostingServiceV2 service;

    @PostMapping
    public ResponseEntity<AccountPostingCreateResponseV2> create(
            @RequestBody IncomingPostingRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(service.create(request));
    }

    @PostMapping("/search")
    public ResponseEntity<PostingSearchResponseV2> search(
            @RequestBody(required = false) PostingSearchRequestV2 request) {
        return ResponseEntity.ok(service.search(request != null ? request : new PostingSearchRequestV2()));
    }

    @GetMapping("/{postingId}")
    public ResponseEntity<AccountPostingFullResponseV2> findById(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.findById(postingId));
    }

    @PostMapping("/retry")
    public ResponseEntity<RetryResponseV2> retry(@Valid @RequestBody RetryRequestV2 request) {
        return ResponseEntity.ok(service.retry(request));
    }

}
