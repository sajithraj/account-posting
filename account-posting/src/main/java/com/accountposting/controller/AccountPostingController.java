package com.accountposting.controller;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountposting.AccountPostingSearchRequest;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import com.accountposting.service.AccountPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account-posting")
@RequiredArgsConstructor
public class AccountPostingController {

    private final AccountPostingService service;

    @PostMapping
    public ResponseEntity<AccountPostingCreateResponse> create(
            @Valid @RequestBody AccountPostingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public ResponseEntity<Page<AccountPostingResponse>> search(
            @ModelAttribute AccountPostingSearchRequest criteria,
            @PageableDefault(size = 20, sort = "postingId") Pageable pageable) {
        return ResponseEntity.ok(service.search(criteria, pageable));
    }

    @GetMapping("/{postingId}")
    public ResponseEntity<AccountPostingResponse> findById(@PathVariable Long postingId) {
        return ResponseEntity.ok(service.findById(postingId));
    }

    @PostMapping("/retry")
    public ResponseEntity<RetryResponse> retry(@RequestBody(required = false) RetryRequest request) {
        RetryRequest retryRequest = request != null ? request : new RetryRequest();
        return ResponseEntity.ok(service.retry(retryRequest));
    }
}
