package com.accountposting.entity;

import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
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

import java.time.Instant;

@Entity
@Table(name = "account_posting_leg")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPostingLegEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
    @Builder.Default
    private LegStatus status = LegStatus.PENDING;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "posted_time")
    private Instant postedTime;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload")
    private String responsePayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 10)
    @Builder.Default
    private LegMode mode = LegMode.NORM;

    @Column(name = "operation", nullable = false, length = 20)
    @Builder.Default
    private String operation = "POSTING";
}
