package io.github.adarko22.customeractivityanalytics.transaction.card;

import io.github.adarko22.customeractivityanalytics.transaction.TransactionSpecifications;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class CardActivitySpecifications {

  private CardActivitySpecifications() {}

  public static Specification<CardActivity> filter(
      UUID customerId,
      TransactionStatus status,
      Instant from,
      Instant to,
      BigDecimal minAmount,
      BigDecimal maxAmount,
      String currency,
      String cardType,
      String merchantName,
      String mccCode,
      Boolean cardPresent) {
    return TransactionSpecifications.<CardActivity>common(
            customerId, status, from, to, minAmount, maxAmount, currency)
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
