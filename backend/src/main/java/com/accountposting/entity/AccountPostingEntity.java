package com.accountposting.entity;

import com.accountposting.entity.enums.CreditDebitIndicator;
import com.accountposting.entity.enums.PostingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "account_posting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "posting_id")
    private Long postingId;

    @Column(name = "source_reference_id", nullable = false, length = 100)
    private String sourceReferenceId;

    @Column(name = "end_to_end_reference_id", nullable = false, unique = true, length = 100)
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
    @Builder.Default
    private PostingStatus status = PostingStatus.PNDG;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    private String responsePayload;

    @Column(name = "retry_locked_until")
    private Instant retryLockedUntil;

    @Column(name = "target_systems", length = 500)
    private String targetSystems;

    @Column(name = "reason", length = 1000)
    private String reason;
}
