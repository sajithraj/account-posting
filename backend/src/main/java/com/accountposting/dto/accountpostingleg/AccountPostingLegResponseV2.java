package com.accountposting.dto.accountpostingleg;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class AccountPostingLegResponseV2 {

    private Long postingLegId;
    private Long postingId;
    private Integer legOrder;
    private String targetSystem;
    private String account;
    private LegStatus status;
    private String referenceId;
    private String reason;
    private Integer attemptNumber;
    private Instant postedTime;
    private LegMode mode;
    private String operation;
    private Instant createdAt;
    private Instant updatedAt;
}
