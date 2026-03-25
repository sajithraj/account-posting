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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Read/write mirror of {@link AccountPostingLegEntity} stored in {@code account_posting_leg_history}.
 * Rows are inserted by {@link com.accountposting.service.archival.ArchivalService} and are
 * never modified after that. Does NOT extend BaseEntity — audit timestamps are copied verbatim.
 */
@Entity
@Table(name = "account_posting_leg_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingLegHistoryEntity {

    /**
     * Same value as the original {@code posting_leg_id}. No auto-generation.
     */
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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    private LegMode mode;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    /**
     * Copied from the original row — not JPA-audited.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Copied from the original row — not JPA-audited.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Timestamp set by the archival job when this row was moved to history.
     */
    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;
}
