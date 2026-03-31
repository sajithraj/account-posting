package com.sajith.payments.redesign.repository;

import com.sajith.payments.redesign.entity.AccountPostingLegHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountPostingLegHistoryRepository extends JpaRepository<AccountPostingLegHistoryEntity, Long> {

    List<AccountPostingLegHistoryEntity> findByPostingIdOrderByTransactionOrder(Long postingId);
}
