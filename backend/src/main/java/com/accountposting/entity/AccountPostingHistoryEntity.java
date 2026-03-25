package com.accountposting.entity;

import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Read/write mirror of {@link AccountPostingEntity} stored in {@code account_posting_history}.
 * Rows are inserted by {@link com.accountposting.service.archival.ArchivalService} and are
 * never modified after that. Does NOT extend BaseEntity - audit timestamps are copied verbatim
 * from the original row; JPA auditing must not overwrite them.
 */
@Entity
@Table(name = "account_posting_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingHistoryEntity {

    /**
     * Same value as the original {@code posting_id}. No auto-generation.
     */
    @Id
    @Column(name = "posting_id")
    private Long postingId;

    @Column(name = "source_reference_id", nullable = false, length = 100)
    private String sourceReferenceId;

    @Column(name = "end_to_end_reference_id", nullable = false, length = 100)
    private String endToEndReferenceId;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_debit_indicator", nullable = false, length = 6)
    private CreditDebitIndicator creditDebitIndicator;

    @Column(name = "debtor_account", nullable = false, length = 50)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false, length = 50)
    private String creditorAccount;

    @Column(name = "requested_execution_date", nullable = false)
    private LocalDate requestedExecutionDate;

    @Column(name = "remittance_information", length = 500)
    private String remittanceInformation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PostingStatus status;

    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "retry_locked_until")
    private Instant retryLockedUntil;

    @Column(name = "target_systems", length = 500)
    private String targetSystems;

    @Column(name = "reason", length = 1000)
    private String reason;

    /**
     * Copied from the original row - not JPA-audited.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Copied from the original row - not JPA-audited.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Timestamp set by the archival job when this row was moved to history.
     */
    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;
}
