package com.accountposting.repository;

import com.accountposting.entity.AccountPostingLegEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountPostingLegRepository extends JpaRepository<AccountPostingLegEntity, Long> {

    List<AccountPostingLegEntity> findByPostingIdOrderByLegOrder(Long postingId);

    Optional<AccountPostingLegEntity> findByPostingLegIdAndPostingId(Long postingLegId, Long postingId);

    /**
     * Fetches all legs for the given postingIds.
     */
    List<AccountPostingLegEntity> findByPostingIdIn(Collection<Long> postingIds);

    /**
     * Returns all non-SUCCESS legs for a posting ordered by legOrder.
     */
    @Query("""
            SELECT l FROM AccountPostingLegEntity l
            WHERE l.postingId = :postingId
              AND l.status <> :status
            ORDER BY l.legOrder
            """)
    List<AccountPostingLegEntity> findNonSuccessByPostingId(
            @Param("postingId") Long postingId,
            @Param("status") com.accountposting.entity.enums.LegStatus status);
}
