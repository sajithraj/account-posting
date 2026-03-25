package com.accountposting.repository;

import com.accountposting.entity.AccountPostingLegHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountPostingLegHistoryRepository extends JpaRepository<AccountPostingLegHistoryEntity, Long> {

    List<AccountPostingLegHistoryEntity> findByPostingIdOrderByLegOrder(Long postingId);
}
