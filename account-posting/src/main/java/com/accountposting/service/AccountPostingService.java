package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountposting.AccountPostingSearchRequest;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AccountPostingService {

    AccountPostingCreateResponse create(AccountPostingRequest request);

    Page<AccountPostingResponse> search(AccountPostingSearchRequest criteria, Pageable pageable);

    AccountPostingResponse findById(Long postingId);

    RetryResponse retry(RetryRequest request);

    /**
     * Returns distinct targetSystems values matching the given fragment (for autocomplete).
     */
    List<String> getTargetSystems(String q);
}
