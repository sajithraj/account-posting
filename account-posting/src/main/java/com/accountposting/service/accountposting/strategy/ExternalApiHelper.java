package com.accountposting.service.accountposting.strategy;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds external API request payloads and provides stub responses for CBS, GL, and OBPM.
 * Replace each call* method with the real HTTP integration when available.
 */
@Component
public class ExternalApiHelper {

    // ── CBS ───────────────────────────────────────────────────────────────────

    public Map<String, Object> buildCbsRequest(AccountPostingRequest request, String transactionIndex) {
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

    /**
     * Stub — replace with real CBS HTTP call. Response: { transaction_index, status: SUCCESS|FAILURE }
     */
    public Map<String, Object> callCbs(Map<String, Object> cbsRequest, String transactionIndex) {
        // TODO: replace with actual CBS HTTP integration
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        return response;
    }

    // ── GL ────────────────────────────────────────────────────────────────────

    public Map<String, Object> buildGlRequest(AccountPostingRequest request) {
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

    /**
     * Stub — replace with real GL HTTP call. Response: { responder_ref_id, status: SUCCESS|FAILURE }
     */
    public Map<String, Object> callGl(Map<String, Object> glRequest) {
        // TODO: replace with actual GL HTTP integration
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responder_ref_id", UUID.randomUUID().toString());
        response.put("status", "SUCCESS");
        return response;
    }

    // ── OBPM ──────────────────────────────────────────────────────────────────

    public Map<String, Object> buildObpmRequest(AccountPostingRequest request) {
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

    /**
     * Stub — replace with real OBPM HTTP call. Response: { transaction_id, status: SUCCESS|FAILURE }
     */
    public Map<String, Object> callObpm(Map<String, Object> obpmRequest) {
        // TODO: replace with actual OBPM HTTP integration
        String transactionId = "TRAN" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
        response.put("status", "SUCCESS");
        return response;
    }
}
