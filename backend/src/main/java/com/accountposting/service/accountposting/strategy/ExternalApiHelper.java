package com.accountposting.service.accountposting.strategy;

import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock responses for diff target system & operation
 */
@Component
public class ExternalApiHelper {

    // -- CBS ------------------------------------------------------------------
    public Map<String, Object> buildCbsRequest(AccountPostingRequestV2 request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("amount", request.getAmount());
        req.put("description1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("transaction_code", 9034);
        req.put("tell_id", 4516);
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbs(Map<String, Object> cbsRequest, String transactionIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        return response;
    }

    // -- CBS ADD_HOLD ---------------------------------------------------------
    public Map<String, Object> buildCbsAddHoldRequest(AccountPostingRequestV2 request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("operation", "ADD_HOLD");
        req.put("amount", request.getAmount());
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbsAddHold(Map<String, Object> cbsRequest, String transactionIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        return response;
    }

    // -- CBS REMOVE_HOLD ------------------------------------------------------
    public Map<String, Object> buildCbsRemoveHoldRequest(AccountPostingRequestV2 request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("operation", "REMOVE_HOLD");
        req.put("amount", request.getAmount());
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbsRemoveHold(Map<String, Object> cbsRequest, String transactionIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        return response;
    }

    // -- GL -------------------------------------------------------------------
    public Map<String, Object> buildGlRequest(AccountPostingRequestV2 request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "GL");
        req.put("amount", request.getAmount());
        req.put("rem_info", List.of(request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment"));
        req.put("dept_code", 12);
        req.put("gl_account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callGl(Map<String, Object> glRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responder_ref_id", UUID.randomUUID().toString());
        response.put("status", "SUCCESS");
        return response;
    }

    // -- OBPM -----------------------------------------------------------------
    public Map<String, Object> buildObpmRequest(AccountPostingRequestV2 request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "OBPM");
        req.put("amount", request.getAmount());
        req.put("currency", request.getCurrency());
        req.put("remit_info1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("mca_code", 97);
        req.put("mca_account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callObpm(Map<String, Object> obpmRequest) {
        String transactionId = "TRAN" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
        response.put("status", "SUCCESS");
        return response;
    }
}
