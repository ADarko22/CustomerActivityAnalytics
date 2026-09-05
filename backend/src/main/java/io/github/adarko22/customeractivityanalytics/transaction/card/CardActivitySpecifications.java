package io.github.adarko22.customeractivityanalytics.transaction.card;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionCommonFilters;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Query predicates for {@link CardActivity}: the shared transaction filters plus card-specific
 * fields.
 */
public final class CardActivitySpecifications {

  private CardActivitySpecifications() {}

  public static Specification<CardActivity> filter(
      UUID customerId,
      TransactionCommonFilters filters,
      String cardType,
      String merchantName,
      String mccCode,
      Boolean cardPresent) {
    return TransactionSpecifications.<CardActivity>common(
            customerId,
            filters.status(),
            filters.from(),
            filters.to(),
            filters.minAmount(),
            filters.maxAmount(),
            filters.currency())
        .and(typeFilters(cardType, merchantName, mccCode, cardPresent));
  }

  private static Specification<CardActivity> typeFilters(
      String cardType, String merchantName, String mccCode, Boolean cardPresent) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (cardType != null && !cardType.isBlank()) {
        predicates.add(cb.equal(cb.upper(root.get("cardType")), cardType.toUpperCase()));
      }
      if (merchantName != null && !merchantName.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("merchantName")), "%" + merchantName.toLowerCase() + "%"));
      }
      if (mccCode != null && !mccCode.isBlank()) {
        predicates.add(cb.equal(root.get("mccCode"), mccCode));
      }
      if (cardPresent != null) {
        predicates.add(cb.equal(root.get("cardPresent"), cardPresent));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
