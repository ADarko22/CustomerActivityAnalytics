package io.github.adarko22.customeractivityanalytics.risk;

import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Predicates for the AI risk-assessment history endpoint. {@link RiskFinalAssessment} carries no
 * {@code customerId} column of its own, so customer scoping is a correlated subquery against {@link
 * Transaction} rather than a mapped JPA relationship — avoids adding a navigation-only association
 * the rest of the domain doesn't need.
 */
public final class RiskFinalAssessmentSpecifications {

  private RiskFinalAssessmentSpecifications() {}

  public static Specification<RiskFinalAssessment> filter(
      UUID customerId,
      UUID transactionId,
      RiskLevel riskLevel,
      Instant from,
      Instant to,
      BigDecimal minScore,
      BigDecimal maxScore) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(
          root.get("transactionId").in(transactionIdsForCustomer(query, cb, customerId)));
      if (transactionId != null) {
        predicates.add(cb.equal(root.get("transactionId"), transactionId));
      }
      if (riskLevel != null) {
        predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("triggeredAt"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("triggeredAt"), to));
      }
      if (minScore != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("riskScore"), minScore));
      }
      if (maxScore != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("riskScore"), maxScore));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private static Subquery<UUID> transactionIdsForCustomer(
      CriteriaQuery<?> query, CriteriaBuilder cb, UUID customerId) {
    Subquery<UUID> subquery = query.subquery(UUID.class);
    Root<Transaction> transactionRoot = subquery.from(Transaction.class);
    subquery.select(transactionRoot.get("transactionId"));
    subquery.where(cb.equal(transactionRoot.get("customerId"), customerId));
    return subquery;
  }
}
