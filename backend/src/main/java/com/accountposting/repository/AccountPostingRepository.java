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

import java.time.Instant;
import java.util.List;

@Repository
public interface AccountPostingRepository
        extends JpaRepository<AccountPostingEntity, Long>, JpaSpecificationExecutor<AccountPostingEntity> {

    boolean existsByEndToEndReferenceId(String endToEndReferenceId);

    /**
     * All PENDING postings not currently locked — used when no specific IDs are requested.
     */
    @Query("""
            SELECT p FROM AccountPostingEntity p
            WHERE p.status = :status
              AND (p.retryLockedUntil IS NULL OR p.retryLockedUntil < :now)
            """)
    List<AccountPostingEntity> findEligibleForRetry(
            @Param("status") PostingStatus status,
            @Param("now") Instant now);

    /**
     * Atomically locks the given postings for retry by setting retryLockedUntil.
     * Only locks postings that are PENDING and not currently locked.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AccountPostingEntity p
            SET p.retryLockedUntil = :lockUntil
            WHERE p.postingId IN :ids
              AND p.status = :status
              AND (p.retryLockedUntil IS NULL OR p.retryLockedUntil < :now)
            """)
    int lockForRetry(
            @Param("ids") List<Long> ids,
            @Param("status") PostingStatus status,
            @Param("now") Instant now,
            @Param("lockUntil") Instant lockUntil);

    /**
     * Fetch postings that were just locked — matched by the exact lockUntil timestamp.
     */
    @Query("""
            SELECT p FROM AccountPostingEntity p
            WHERE p.postingId IN :ids
              AND p.retryLockedUntil = :lockUntil
            """)
    List<AccountPostingEntity> findByIdsAndLockUntil(
            @Param("ids") List<Long> ids,
            @Param("lockUntil") Instant lockUntil);

    /**
     * Returns the oldest postings whose {@code createdAt} is before {@code threshold},
     * ordered by {@code createdAt} ascending so that the earliest records are archived first.
     * Uses {@link Pageable} to process in fixed-size batches.
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
