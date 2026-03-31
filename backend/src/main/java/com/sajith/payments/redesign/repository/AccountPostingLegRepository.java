package com.sajith.payments.redesign.repository;

import com.sajith.payments.redesign.entity.AccountPostingLegEntity;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountPostingLegRepository extends JpaRepository<AccountPostingLegEntity, Long> {

    List<AccountPostingLegEntity> findByPostingIdOrderByTransactionOrder(Long postingId);

    Optional<AccountPostingLegEntity> findByTransactionIdAndPostingId(Long transactionId, Long postingId);

    List<AccountPostingLegEntity> findByPostingIdIn(Collection<Long> postingIds);

    @Query("""
            SELECT l FROM AccountPostingLegEntity l
            WHERE l.postingId = :postingId
              AND l.status <> :status
            ORDER BY l.transactionOrder
            """)
    List<AccountPostingLegEntity> findNonSuccessByPostingId(
            @Param("postingId") Long postingId,
            @Param("status") LegStatus status);
}
