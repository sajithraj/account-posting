package com.sajith.payments.redesign.repository;

import com.sajith.payments.redesign.entity.AccountPostingHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountPostingHistoryRepository
        extends JpaRepository<AccountPostingHistoryEntity, Long>,
        JpaSpecificationExecutor<AccountPostingHistoryEntity> {
}
