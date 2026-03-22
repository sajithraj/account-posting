package com.accountposting.service;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.entity.enums.RequestType;
import com.accountposting.entity.enums.SourceName;
import com.accountposting.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Validates incoming posting requests.
 *
 * <p>Two-phase validation:
 * <ol>
 *   <li>{@link #validateFields(AccountPostingRequest)} — structural checks (null / blank / size /
 *       format). Called <em>before</em> the initial DB save. Failures → 400, no DB entry
 *       (impossible to persist without required NOT-NULL columns).</li>
 *   <li>{@link #validateEnums(String, String)} — enum membership checks. Called <em>after</em>
 *       the initial PENDING save so that non-null but invalid enum values are always recorded in
 *       the DB with FAILED status for audit visibility. Failures → 400.</li>
 * </ol>
 */
@Slf4j
@Component
public class AccountPostingRequestValidator {

    private static final List<String> VALID_SOURCE_NAMES =
            Arrays.stream(SourceName.values()).map(Enum::name).toList();

    private static final List<String> VALID_REQUEST_TYPES =
            Arrays.stream(RequestType.values()).map(Enum::name).toList();

    /**
     * Validates all required and size-constrained fields.
     * Call before persisting the posting.
     *
     * @throws BusinessException with code {@code VALIDATION_FAILED} listing all errors.
     */
    public void validateFields(AccountPostingRequest request) {
        List<String> errors = new ArrayList<>();

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
    }

    /**
     * Validates that sourceName and requestType match known enum constants.
     * Uses list-contains check instead of try/catch.
     * Call <em>after</em> the initial PENDING save for full audit visibility.
     *
     * @throws BusinessException with code {@code INVALID_ENUM_VALUE} → HTTP 400.
     */
    public void validateEnums(String sourceName, String requestType) {
        List<String> errors = new ArrayList<>();

        if (!VALID_SOURCE_NAMES.contains(sourceName))
            errors.add("sourceName '" + sourceName + "' is not valid. Accepted values: " + VALID_SOURCE_NAMES);

        if (!VALID_REQUEST_TYPES.contains(requestType))
            errors.add("requestType '" + requestType + "' is not valid. Accepted values: " + VALID_REQUEST_TYPES);

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
