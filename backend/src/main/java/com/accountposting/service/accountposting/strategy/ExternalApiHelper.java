package com.accountposting.service.accountposting.strategy;

import com.accountposting.config.StubSimulatorConfig;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds outbound request payloads for each external system and holds stub call implementations.
 * Each call* method must be replaced with the real HTTP client integration before go-live.
 * <p>
 * In non-prod environments, StubSimulatorConfig can force specific systems to return FAILED
 * so failure paths can be tested end-to-end and persisted to the database.
 */
@Component
@RequiredArgsConstructor
public class ExternalApiHelper {

    private final StubSimulatorConfig stubSimulatorConfig;

    // ── CBS ───────────────────────────────────────────────────────────────────

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

    // TODO: replace with real CBS HTTP client
    public Map<String, Object> callCbs(Map<String, Object> cbsRequest, String transactionIndex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        if (stubSimulatorConfig.shouldFail("CBS_POSTING")) {
            response.put("status", "FAILED");
            response.put("reason", "Simulated CBS failure — configured via /test/stub/configure");
            return response;
        }
        response.put("status", "SUCCESS");
        return response;
    }

    // ── GL ────────────────────────────────────────────────────────────────────

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

    // TODO: replace with real GL HTTP client
    public Map<String, Object> callGl(Map<String, Object> glRequest) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responder_ref_id", UUID.randomUUID().toString());
        if (stubSimulatorConfig.shouldFail("GL_POSTING")) {
            response.put("status", "FAILED");
            response.put("reason", "Simulated GL failure — configured via /test/stub/configure");
            return response;
        }
        response.put("status", "SUCCESS");
        return response;
    }

    // ── OBPM ──────────────────────────────────────────────────────────────────

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

    // TODO: replace with real OBPM HTTP client
    public Map<String, Object> callObpm(Map<String, Object> obpmRequest) {
        String transactionId = "TRAN" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
        if (stubSimulatorConfig.shouldFail("OBPM_POSTING")) {
            response.put("status", "FAILED");
            response.put("reason", "Simulated OBPM failure — configured via /test/stub/configure");
            return response;
        }
        response.put("status", "SUCCESS");
        return response;
    }
}
