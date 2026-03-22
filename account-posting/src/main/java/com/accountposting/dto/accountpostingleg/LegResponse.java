package com.accountposting.dto.accountpostingleg;

import lombok.Data;

import java.time.Instant;

@Data
public class LegResponse {
    private Long postingLegId;
    private Integer legOrder;
    /**
     * Target system name — CBS / GL / OBPM
     */
    private String name;
    /**
     * Operation type — POSTING / ADD_HOLD / CANCEL_HOLD
     */
    private String type;
    private String account;
    private String referenceId;
    private Instant postedTime;
    private String status;
    private String reason;
    private String mode;
}
