package com.accountposting.repository;

import com.accountposting.entity.AccountPosting;
import com.accountposting.entity.enums.PostingStatus;
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
        extends JpaRepository<AccountPosting, Long>, JpaSpecificationExecutor<AccountPosting> {

    boolean existsByEndToEndReferenceId(String endToEndReferenceId);

    /**
     * Returns distinct non-null targetSystems values that contain the given fragment.
     * Used by the target-systems autocomplete endpoint.
     */
    @Query("""
            SELECT DISTINCT p.targetSystems FROM AccountPosting p
            WHERE p.targetSystems IS NOT NULL
              AND (:q IS NULL OR p.targetSystems LIKE %:q%)
            ORDER BY p.targetSystems
            """)
    List<String> findDistinctTargetSystems(@Param("q") String q);

    /**
     * All PENDING postings not currently locked — used when no specific IDs are requested.
     */
    @Query("""
            SELECT p FROM AccountPosting p
            WHERE p.status = :status
              AND (p.retryLockedUntil IS NULL OR p.retryLockedUntil < :now)
            """)
    List<AccountPosting> findEligibleForRetry(
            @Param("status") PostingStatus status,
            @Param("now") Instant now);

    /**
     * Atomically locks the given postings for retry by setting retryLockedUntil.
     * Only locks postings that are PENDING and not currently locked.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AccountPosting p
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
            SELECT p FROM AccountPosting p
            WHERE p.postingId IN :ids
              AND p.retryLockedUntil = :lockUntil
            """)
    List<AccountPosting> findByIdsAndLockUntil(
            @Param("ids") List<Long> ids,
            @Param("lockUntil") Instant lockUntil);
}
