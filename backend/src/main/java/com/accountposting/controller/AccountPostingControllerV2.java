package com.accountposting.controller;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountposting.AccountPostingResponseV2;
import com.accountposting.dto.accountposting.AccountPostingSearchRequestV2;
import com.accountposting.dto.retry.RetryRequestV2;
import com.accountposting.dto.retry.RetryResponseV2;
import com.accountposting.service.accountposting.AccountPostingServiceV2;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
            @Valid @RequestBody AccountPostingRequestV2 request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<AccountPostingResponseV2>> search(
            @ModelAttribute AccountPostingSearchRequestV2 criteria,
            @PageableDefault(size = 20, sort = "postingId") Pageable pageable) {
        return ResponseEntity.ok(service.search(criteria, pageable));
    }

    @GetMapping("/{postingId}")
    public ResponseEntity<AccountPostingResponseV2> findById(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.findById(postingId));
    }

    @PostMapping("/retry")
    public ResponseEntity<RetryResponseV2> retry(@RequestBody(required = false) RetryRequestV2 request) {
        RetryRequestV2 retryRequest = request != null ? request : new RetryRequestV2();
        return ResponseEntity.ok(service.retry(retryRequest));
    }

    /**
     * Searches the history table (records archived after the configured retention period).
     * Accepts the same filter parameters as {@code GET /v2/payment/account-posting}.
     *
     * <p><b>Enterprise pattern:</b> {@code GET /{id}} is transparent - it checks the active table
     * first and falls back to history automatically. Use this endpoint only when you want to
     * explicitly query historical data in bulk.
     */
    @GetMapping("/history")
    public ResponseEntity<Page<AccountPostingResponseV2>> searchHistory(
            @ModelAttribute AccountPostingSearchRequestV2 criteria,
            @PageableDefault(size = 20, sort = "postingId") Pageable pageable) {
        return ResponseEntity.ok(service.searchHistory(criteria, pageable));
    }
}
