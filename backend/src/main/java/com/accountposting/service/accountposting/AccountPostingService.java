package com.accountposting.service.accountposting;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountposting.AccountPostingSearchRequest;
import com.accountposting.dto.retry.RetryRequest;
import com.accountposting.dto.retry.RetryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountPostingService {

    AccountPostingCreateResponse create(AccountPostingRequest request);

    Page<AccountPostingResponse> search(AccountPostingSearchRequest criteria, Pageable pageable);

    AccountPostingResponse findById(Long postingId);

    RetryResponse retry(RetryRequest request);
}
