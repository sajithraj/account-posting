package com.sr.accountposting.service.strategy;

import com.sr.accountposting.dto.posting.IncomingPostingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class ExternalApiHelper {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiHelper.class);

    @Inject
    public ExternalApiHelper() {
    }

    public Map<String, Object> buildCbsRequest(IncomingPostingRequest request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("amount", request.getAmount() != null ? request.getAmount().getValue() : null);
        req.put("description1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("transaction_code", 9034);
        req.put("tell_id", 4516);
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbs(Map<String, Object> cbsRequest, String transactionIndex) {
        log.info("callCbs transactionIndex={} request={}", transactionIndex, cbsRequest);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        response.put("posted_time", Instant.now().toString());
        log.info("callCbs transactionIndex={} response={}", transactionIndex, response);
        return response;
    }

    public Map<String, Object> buildCbsAddHoldRequest(IncomingPostingRequest request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("operation", "ADD_HOLD");
        req.put("amount", request.getAmount() != null ? request.getAmount().getValue() : null);
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbsAddHold(Map<String, Object> cbsRequest, String transactionIndex) {
        log.info("callCbsAddHold transactionIndex={} request={}", transactionIndex, cbsRequest);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        response.put("posted_time", Instant.now().toString());
        log.info("callCbsAddHold transactionIndex={} response={}", transactionIndex, response);
        return response;
    }

    public Map<String, Object> buildCbsRemoveHoldRequest(IncomingPostingRequest request, String transactionIndex) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("transaction_index", transactionIndex);
        req.put("target_system", "CBS");
        req.put("operation", "REMOVE_HOLD");
        req.put("amount", request.getAmount() != null ? request.getAmount().getValue() : null);
        req.put("account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callCbsRemoveHold(Map<String, Object> cbsRequest, String transactionIndex) {
        log.info("callCbsRemoveHold transactionIndex={} request={}", transactionIndex, cbsRequest);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_index", transactionIndex);
        response.put("status", "SUCCESS");
        response.put("posted_time", Instant.now().toString());
        log.info("callCbsRemoveHold transactionIndex={} response={}", transactionIndex, response);
        return response;
    }

    public Map<String, Object> buildGlRequest(IncomingPostingRequest request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "GL");
        req.put("amount", request.getAmount() != null ? request.getAmount().getValue() : null);
        req.put("rem_info", List.of(request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment"));
        req.put("dept_code", 12);
        req.put("gl_account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callGl(Map<String, Object> glRequest) {
        log.info("callGl request={}", glRequest);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responder_ref_id", UUID.randomUUID().toString());
        response.put("status", "SUCCESS");
        response.put("posted_time", Instant.now().toString());
        log.info("callGl response={}", response);
        return response;
    }

    public Map<String, Object> buildObpmRequest(IncomingPostingRequest request) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("end_to_end_id", request.getEndToEndReferenceId());
        req.put("target_system", "OBPM");
        req.put("amount", request.getAmount() != null ? request.getAmount().getValue() : null);
        req.put("currency", request.getAmount() != null ? request.getAmount().getCurrencyCode() : null);
        req.put("remit_info1", request.getRemittanceInformation() != null
                ? request.getRemittanceInformation() : "Payment");
        req.put("mca_code", 97);
        req.put("mca_account", request.getDebtorAccount());
        return req;
    }

    public Map<String, Object> callObpm(Map<String, Object> obpmRequest) {
        log.info("callObpm request={}", obpmRequest);
        String transactionId = "TRAN" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
                .format(LocalDateTime.now());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("transaction_id", transactionId);
        response.put("status", "SUCCESS");
        response.put("posted_time", Instant.now().toString());
        log.info("callObpm response={}", response);
        return response;
    }
}
