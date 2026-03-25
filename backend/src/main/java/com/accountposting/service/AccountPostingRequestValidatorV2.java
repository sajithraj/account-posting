package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.entity.enums.RequestType;
import com.accountposting.entity.enums.SourceName;
import com.accountposting.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class AccountPostingRequestValidatorV2 {

    private static final List<String> VALID_SOURCE_NAMES =
            Arrays.stream(SourceName.values()).map(Enum::name).toList();

    private static final List<String> VALID_REQUEST_TYPES =
            Arrays.stream(RequestType.values()).map(Enum::name).toList();

    /**
     * Validates all required fields, size constraints, and enum membership in one pass.
     *
     * @throws BusinessException on the first category that produces errors.
     */
    public void validate(AccountPostingRequestV2 request) {
        List<String> errors = new ArrayList<>();

        // ── Field / structural checks ──────────────────────────────────────
        if (isBlank(request.getSourceReferenceId()))
            errors.add("sourceReferenceId is required");
        else if (request.getSourceReferenceId().length() > 100)
            errors.add("sourceReferenceId must not exceed 100 characters");

        if (isBlank(request.getEndToEndReferenceId()))
            errors.add("endToEndReferenceId is required");
        else if (request.getEndToEndReferenceId().length() > 100)
            errors.add("endToEndReferenceId must not exceed 100 characters");

        if (isBlank(request.getSourceName()))
            errors.add("sourceName is required");

        if (isBlank(request.getRequestType()))
            errors.add("requestType is required");

        if (request.getAmount() == null)
            errors.add("amount is required");
        else if (request.getAmount().compareTo(new BigDecimal("0.0001")) < 0)
            errors.add("amount must be greater than zero");

        if (isBlank(request.getCurrency()))
            errors.add("currency is required");
        else if (request.getCurrency().trim().length() != 3)
            errors.add("currency must be a 3-letter ISO code");

        if (request.getCreditDebitIndicator() == null)
            errors.add("creditDebitIndicator is required");

        if (isBlank(request.getDebtorAccount()))
            errors.add("debtorAccount is required");
        else if (request.getDebtorAccount().length() > 50)
            errors.add("debtorAccount must not exceed 50 characters");

        if (isBlank(request.getCreditorAccount()))
            errors.add("creditorAccount is required");
        else if (request.getCreditorAccount().length() > 50)
            errors.add("creditorAccount must not exceed 50 characters");

        if (request.getRequestedExecutionDate() == null)
            errors.add("requestedExecutionDate is required");

        if (request.getRemittanceInformation() != null
                && request.getRemittanceInformation().length() > 500)
            errors.add("remittanceInformation must not exceed 500 characters");

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            log.warn("VALIDATION_FAILED | {}", message);
            throw new BusinessException("VALIDATION_FAILED", message);
        }

        // ── Enum membership checks ─────────────────────────────────────────
        errors = new ArrayList<>();

        if (!isBlank(request.getSourceName()) && !VALID_SOURCE_NAMES.contains(request.getSourceName()))
            errors.add("sourceName '" + request.getSourceName() + "' is not valid. Accepted values: " + VALID_SOURCE_NAMES);

        if (!isBlank(request.getRequestType()) && !VALID_REQUEST_TYPES.contains(request.getRequestType()))
            errors.add("requestType '" + request.getRequestType() + "' is not valid. Accepted values: " + VALID_REQUEST_TYPES);

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            log.warn("INVALID_ENUM_VALUE | {}", message);
            throw new BusinessException("INVALID_ENUM_VALUE", message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
