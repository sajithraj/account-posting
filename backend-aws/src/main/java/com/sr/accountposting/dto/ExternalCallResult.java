package com.sr.accountposting.dto;

import com.sr.accountposting.enums.LegStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExternalCallResult {
    private LegStatus status;      // SUCCESS or FAILED
    private String referenceId;
    private String postedTime;
    private String reason;
    private String requestPayload;
    private String responsePayload;
}
