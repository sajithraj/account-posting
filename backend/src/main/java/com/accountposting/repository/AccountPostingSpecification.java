package com.accountposting.repository;

import com.accountposting.dto.accountposting.AccountPostingSearchRequestV2;
import com.accountposting.entity.AccountPostingEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public final class AccountPostingSpecification {

    private AccountPostingSpecification() {
    }

    public static Specification<AccountPostingEntity> from(AccountPostingSearchRequestV2 criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }
            if (StringUtils.hasText(criteria.getEndToEndReferenceId())) {
                predicates.add(cb.equal(root.get("endToEndReferenceId"),
                        criteria.getEndToEndReferenceId()));
            }
            if (StringUtils.hasText(criteria.getSourceReferenceId())) {
                predicates.add(cb.equal(root.get("sourceReferenceId"),
                        criteria.getSourceReferenceId()));
            }
            if (StringUtils.hasText(criteria.getSourceName())) {
                predicates.add(cb.equal(root.get("sourceName"), criteria.getSourceName()));
            }
            if (StringUtils.hasText(criteria.getRequestType())) {
                predicates.add(cb.equal(root.get("requestType"), criteria.getRequestType()));
            }
            if (StringUtils.hasText(criteria.getTargetSystem())) {
                predicates.add(cb.like(root.get("targetSystems"),
                        "%" + criteria.getTargetSystem() + "%"));
            }
            if (criteria.getFromDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("requestedExecutionDate"), criteria.getFromDate()));
            }
            if (criteria.getToDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("requestedExecutionDate"), criteria.getToDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
