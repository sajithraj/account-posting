package com.accountposting.entity;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "account_posting_leg_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingLegHistoryEntity {

    @Id
    @Column(name = "posting_leg_id")
    private Long postingLegId;

    @Column(name = "posting_id", nullable = false)
    private Long postingId;

    @Column(name = "leg_order", nullable = false)
    private Integer legOrder;

    @Column(name = "target_system", nullable = false, length = 100)
    private String targetSystem;

    @Column(name = "account", nullable = false, length = 50)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private LegStatus status;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "posted_time")
    private Instant postedTime;

    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private LegMode mode;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;
}
