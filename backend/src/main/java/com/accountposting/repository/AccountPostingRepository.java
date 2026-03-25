package com.accountposting.repository;

import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.enums.PostingStatus;
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
     * Call {@link #lockEligibleByIds} immediately after to acquire the lock.
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
     * Locks the supplied posting IDs by setting {@code retryLockedUntil = lockUntil}.
     * Only rows that are still PNDG with no active lock are updated — already-locked or
     * non-PNDG rows are silently skipped.
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
     * Returns the oldest postings whose {@code createdAt} is before {@code threshold},
     * ordered by {@code createdAt} ascending so that the earliest records are archived first.
     */
    @Query("""
            SELECT p FROM AccountPostingEntity p
            WHERE p.createdAt < :threshold
            ORDER BY p.createdAt ASC
            """)
    List<AccountPostingEntity> findEligibleForArchival(
            @Param("threshold") Instant threshold,
            Pageable pageable);
}
