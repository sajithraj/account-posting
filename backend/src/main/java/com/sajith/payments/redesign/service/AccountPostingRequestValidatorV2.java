package com.sajith.payments.redesign.service;

import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.entity.enums.CreditDebitIndicator;
import com.sajith.payments.redesign.entity.enums.RequestType;
import com.sajith.payments.redesign.entity.enums.SourceName;
import com.sajith.payments.redesign.exception.BusinessException;
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

    public void validate(IncomingPostingRequest request) {
        List<String> errors = new ArrayList<>();

        if (isBlank(request.getSourceRefId()))
            errors.add("sourceReferenceId is required");
        else if (request.getSourceRefId().length() > 100)
            errors.add("sourceReferenceId must not exceed 100 characters");

        if (isBlank(request.getEndToEndRefId()))
            errors.add("endToEndReferenceId is required");
        else if (request.getEndToEndRefId().length() > 100)
            errors.add("endToEndReferenceId must not exceed 100 characters");

        if (isBlank(request.getSourceName()))
            errors.add("sourceName is required");

        if (isBlank(request.getRequestType()))
            errors.add("requestType is required");

        if (request.getAmount() == null || isBlank(request.getAmount().getValue())) {
            errors.add("amount is required");
        } else {
            try {
                BigDecimal amountValue = new BigDecimal(request.getAmount().getValue());
                if (amountValue.compareTo(new BigDecimal("0.0001")) < 0)
                    errors.add("amount must be greater than zero");
            } catch (NumberFormatException e) {
                errors.add("amount value is not a valid number");
            }
        }

        if (request.getAmount() == null || isBlank(request.getAmount().getCurrency()))
            errors.add("currency is required");
        else if (request.getAmount().getCurrency().trim().length() != 3)
            errors.add("currency must be a 3-letter ISO code");

        if (isBlank(request.getCreditDebitIndicator()))
            errors.add("creditDebitIndicator is required");

        if (isBlank(request.getDebtorAccount()))
            errors.add("debtorAccount is required");
        else if (request.getDebtorAccount().length() > 50)
            errors.add("debtorAccount must not exceed 50 characters");

        if (isBlank(request.getCreditorAccount()))
            errors.add("creditorAccount is required");
        else if (request.getCreditorAccount().length() > 50)
            errors.add("creditorAccount must not exceed 50 characters");

        if (isBlank(request.getRequestedExecutionDate()))
            errors.add("requestedExecutionDate is required");

        if (request.getRemittanceInformation() != null
                && request.getRemittanceInformation().length() > 500)
            errors.add("remittanceInformation must not exceed 500 characters");

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            log.error("VALIDATION_FAILED | {}", message);
            throw new BusinessException("VALIDATION_FAILED", message);
        }

        if (!isBlank(request.getSourceName()) && !VALID_SOURCE_NAMES.contains(request.getSourceName()))
            errors.add("sourceName '" + request.getSourceName() + "' is not valid. Accepted values: " + VALID_SOURCE_NAMES);

        if (!isBlank(request.getRequestType()) && !VALID_REQUEST_TYPES.contains(request.getRequestType()))
            errors.add("requestType '" + request.getRequestType() + "' is not valid. Accepted values: " + VALID_REQUEST_TYPES);

        if (!isBlank(request.getCreditDebitIndicator())) {
            try {
                CreditDebitIndicator.valueOf(request.getCreditDebitIndicator().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("creditDebitIndicator '" + request.getCreditDebitIndicator() + "' is not valid. Accepted values: CREDIT, DEBIT");
            }
        }

        if (!errors.isEmpty()) {
            String message = String.join("; ", errors);
            log.error("INVALID_ENUM_VALUE | {}", message);
            throw new BusinessException("INVALID_ENUM_VALUE", message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
