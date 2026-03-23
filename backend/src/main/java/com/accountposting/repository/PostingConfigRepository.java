package com.accountposting.repository;

import com.accountposting.entity.PostingConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostingConfigRepository extends JpaRepository<PostingConfig, Long> {

    List<PostingConfig> findByRequestTypeOrderByOrderSeqAsc(String requestType);
}
