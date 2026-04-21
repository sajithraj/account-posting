package com.sr.accountposting.validator;

import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import com.sr.accountposting.entity.config.PostingConfigEntity;
import com.sr.accountposting.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Singleton
public class AccountPostingValidator {

    private static final Logger log = LoggerFactory.getLogger(AccountPostingValidator.class);
    private static final Set<String> VALID_CDI = Set.of("CREDIT", "DEBIT");

    @Inject
    public AccountPostingValidator() {
    }

    public void validate(IncomingPostingRequest req, List<PostingConfigEntity> configs) {
        log.info("Validating posting request requestType={} sourceName={} e2eRef={}",
                req.getRequestType(), req.getSourceName(), req.getEndToEndReferenceId());
        List<String> errors = new ArrayList<>();

        if (blank(req.getSourceReferenceId())) errors.add("source_reference_id is required");
        if (blank(req.getEndToEndReferenceId())) errors.add("end_to_end_reference_id is required");

        if (blank(req.getSourceName())) {
            errors.add("source_name is required");
        } else {
            String configuredSource = configs.get(0).getSourceName();
            if (configuredSource != null && !configuredSource.isBlank()
                    && !configuredSource.equals(req.getSourceName())) {
                errors.add("source_name '" + req.getSourceName() + "' is not permitted for requestType="
                        + req.getRequestType() + "; expected: " + configuredSource);
            }
        }

        if (blank(req.getCreditDebitIndicator())) {
            errors.add("credit_debit_indicator is required");
        } else if (!VALID_CDI.contains(req.getCreditDebitIndicator())) {
            errors.add("credit_debit_indicator must be CREDIT or DEBIT");
        }

        if (blank(req.getDebtorAccount())) errors.add("debtor_account is required");
        if (blank(req.getCreditorAccount())) errors.add("creditor_account is required");
        if (blank(req.getRequestedExecutionDate())) errors.add("requested_execution_date is required");

        if (req.getAmount() == null) {
            errors.add("amount is required");
        } else {
            if (blank(req.getAmount().getValue())) {
                errors.add("amount.value is required");
            } else {
                try {
                    BigDecimal amt = new BigDecimal(req.getAmount().getValue());
                    if (amt.compareTo(new BigDecimal("0.0001")) < 0)
                        errors.add("amount.value must be greater than 0.0001");
                } catch (NumberFormatException e) {
                    errors.add("amount.value must be a valid decimal number");
                }
            }
            if (blank(req.getAmount().getCurrencyCode())) {
                errors.add("amount.currency_code is required");
            } else if (req.getAmount().getCurrencyCode().length() != 3) {
                errors.add("amount.currency_code must be 3 characters (ISO 4217)");
            }
        }

        if (!errors.isEmpty()) {
            log.warn("Validation failed requestType={} errors={}", req.getRequestType(), errors);
            throw new ValidationException("VALIDATION_ERROR",
                    "Request validation failed: " + String.join("; ", errors));
        }
        log.info("Validation passed requestType={}", req.getRequestType());
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
