package com.sajith.payments.redesign.repository;

import com.sajith.payments.redesign.dto.search.PostingSearchRequestV2;
import com.sajith.payments.redesign.dto.search.SearchCondition;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class AccountPostingSpecification {

    private AccountPostingSpecification() {
    }

    public static Specification<AccountPostingEntity> from(PostingSearchRequestV2 request) {
        return (root, query, cb) -> {
            if (request.getConditions() == null || request.getConditions().isEmpty()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            for (SearchCondition condition : request.getConditions()) {
                Predicate predicate = buildPredicate(root, cb, condition);
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildPredicate(Root<AccountPostingEntity> root, CriteriaBuilder cb, SearchCondition condition) {
        if (condition.getProperty() == null || condition.getOperator() == null) return null;
        if (condition.getValues() == null || condition.getValues().isEmpty()) return null;

        String field = resolveFieldName(condition.getProperty());
        if (field == null) return null;

        String operator = condition.getOperator().toUpperCase();
        List<String> values = condition.getValues();
        String first = values.get(0);

        return switch (operator) {
            case "EQUALS" -> buildEquals(root, cb, field, first);
            case "IN", "CONTAINS_ANY" -> buildIn(root, cb, field, values);
            case "CONTAINS" -> cb.like(root.get(field), "%" + first + "%");
            case "STARTS_WITH" -> cb.like(root.get(field), first + "%");
            case "ENDS_WITH" -> cb.like(root.get(field), "%" + first);
            case "GREATER_THAN", "GT" -> buildGreaterThan(root, cb, field, first);
            case "LESS_THAN" -> buildLessThan(root, cb, field, first);
            case "BETWEEN" -> buildBetween(root, cb, field, values);
            case "CONTAINS_ALL" -> {
                List<Predicate> all = values.stream()
                        .map(v -> (Predicate) cb.like(root.get(field), "%" + v + "%"))
                        .toList();
                yield cb.and(all.toArray(new Predicate[0]));
            }
            default -> null;
        };
    }

    private static Predicate buildEquals(Root<AccountPostingEntity> root, CriteriaBuilder cb, String field, String value) {
        if ("status".equals(field)) {
            return cb.equal(root.get(field), PostingStatus.valueOf(value.toUpperCase()));
        }
        if (isLocalDateField(field)) {
            return cb.equal(root.get(field), LocalDate.parse(value));
        }
        return cb.equal(root.get(field), value);
    }

    private static Predicate buildIn(Root<AccountPostingEntity> root, CriteriaBuilder cb, String field, List<String> values) {
        if ("status".equals(field)) {
            List<PostingStatus> statuses = values.stream()
                    .map(v -> PostingStatus.valueOf(v.toUpperCase()))
                    .toList();
            return root.get(field).in(statuses);
        }
        return root.get(field).in(values);
    }

    private static Predicate buildGreaterThan(Root<AccountPostingEntity> root, CriteriaBuilder cb, String field, String value) {
        if (isLocalDateField(field)) {
            Path<LocalDate> path = root.get(field);
            return cb.greaterThanOrEqualTo(path, LocalDate.parse(value));
        }
        return cb.greaterThan(root.get(field), value);
    }

    private static Predicate buildLessThan(Root<AccountPostingEntity> root, CriteriaBuilder cb, String field, String value) {
        if (isLocalDateField(field)) {
            Path<LocalDate> path = root.get(field);
            return cb.lessThanOrEqualTo(path, LocalDate.parse(value));
        }
        return cb.lessThan(root.get(field), value);
    }

    private static Predicate buildBetween(Root<AccountPostingEntity> root, CriteriaBuilder cb, String field, List<String> values) {
        if (values.size() < 2) return null;
        if (isLocalDateField(field)) {
            Path<LocalDate> path = root.get(field);
            return cb.between(path, LocalDate.parse(values.get(0)), LocalDate.parse(values.get(1)));
        }
        return cb.between(root.get(field), values.get(0), values.get(1));
    }

    /**
     * Maps governance property names (snake_case) to JPA entity field names (camelCase).
     */
    public static String resolveFieldName(String property) {
        if (property == null) return null;
        return switch (property.toLowerCase()) {
            case "status" -> "status";
            case "source_name" -> "sourceName";
            case "source_reference_id" -> "sourceReferenceId";
            case "end_to_end_reference_id" -> "endToEndReferenceId";
            case "request_type" -> "requestType";
            case "target_system", "target_systems" -> "targetSystems";
            case "requested_execution_date" -> "requestedExecutionDate";
            case "created_at" -> "createdAt";
            case "amount" -> "amount";
            case "currency" -> "currency";
            case "debtor_account" -> "debtorAccount";
            case "creditor_account" -> "creditorAccount";
            default -> null;
        };
    }

    private static boolean isLocalDateField(String field) {
        return "requestedExecutionDate".equals(field);
    }
}
