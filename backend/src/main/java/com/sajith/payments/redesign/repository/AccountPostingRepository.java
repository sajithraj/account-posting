package com.sajith.payments.redesign.repository;

import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface AccountPostingRepository
        extends JpaRepository<AccountPostingEntity, Long>, JpaSpecificationExecutor<AccountPostingEntity> {

    boolean existsByEndToEndReferenceId(String endToEndReferenceId);

    /**
     * Returns IDs of all PNDG postings whose lock has expired (or was never set).
     */
    @Query("""
            SELECT p.postingId FROM AccountPostingEntity p
            WHERE p.status = :status
              AND (p.retryLockedUntil IS NULL OR p.retryLockedUntil < :now)
            """)
    List<Long> findEligibleIdsForRetry(
            @Param("status") PostingStatus status,
            @Param("now") Instant now);

    /**
     * Locks the given posting IDs by setting
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE AccountPostingEntity p
            SET p.retryLockedUntil = :lockUntil
            WHERE p.postingId IN :ids
              AND p.status = :status
              AND (p.retryLockedUntil IS NULL OR p.retryLockedUntil < :now)
            """)
    int lockEligibleByIds(
            @Param("ids") List<Long> ids,
            @Param("status") PostingStatus status,
            @Param("now") Instant now,
            @Param("lockUntil") Instant lockUntil);

    /**
     * Returns the oldest postings which are ready for archival.
     * Only terminal-status postings (ACSP, RJCT) are archived — PNDG postings are excluded.
     */
    @Query("""
            SELECT p FROM AccountPostingEntity p
            WHERE p.createdAt < :threshold
              AND p.status IN (com.sajith.payments.redesign.entity.enums.PostingStatus.ACSP,
                               com.sajith.payments.redesign.entity.enums.PostingStatus.RJCT)
            ORDER BY p.createdAt ASC
            """)
    List<AccountPostingEntity> findEligibleForArchival(
            @Param("threshold") Instant threshold,
            Pageable pageable);
}
