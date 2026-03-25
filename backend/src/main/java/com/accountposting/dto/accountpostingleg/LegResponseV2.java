package com.accountposting.dto.accountpostingleg;

import lombok.Data;

import java.time.Instant;

@Data
public class LegResponseV2 {
    private Long postingLegId;
    private Integer legOrder;
    private String name;
    private String type;
    private String account;
    private String referenceId;
    private Instant postedTime;
    private String status;
    private String reason;
    private String mode;
}
