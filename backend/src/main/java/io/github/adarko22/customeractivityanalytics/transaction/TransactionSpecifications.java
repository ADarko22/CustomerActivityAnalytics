package io.github.adarko22.customeractivityanalytics.transaction;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Predicates shared by the base {@link Transaction} columns, reused by every activity type. */
public final class TransactionSpecifications {

  private TransactionSpecifications() {}

  public static <T extends Transaction> Specification<T> common(
      UUID customerId,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("customerId"), customerId));
      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
      }
      if (minAmount != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
      }
      if (maxAmount != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
      }
      if (currency != null && !currency.isBlank()) {
        predicates.add(cb.equal(cb.upper(root.get("currency")), currency.toUpperCase()));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
