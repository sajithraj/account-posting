package com.sajith.payments.redesign.entity;

import com.sajith.payments.redesign.entity.enums.CreditDebitIndicator;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "account_posting_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingHistoryEntity {

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

    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "request_payload")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.CLOB)
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "retry_locked_until")
    private Instant retryLockedUntil;

    @Column(name = "target_systems", length = 500)
    private String targetSystems;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;
}
