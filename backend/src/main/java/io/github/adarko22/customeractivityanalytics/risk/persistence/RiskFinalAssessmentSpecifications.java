package io.github.adarko22.customeractivityanalytics.risk.persistence;

import io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentProperties;
import io.github.adarko22.customeractivityanalytics.transaction.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

  private static final String RISK_SCORE = "riskScore";

  private RiskFinalAssessmentSpecifications() {}

  public static Specification<RiskFinalAssessment> filter(
      UUID customerId,
      RiskAssessmentHistoryFilters filters,
      RiskAssessmentProperties riskProperties) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(
          root.get("transactionId").in(transactionIdsForCustomer(query, cb, customerId)));
      if (filters.transactionId() != null) {
        predicates.add(cb.equal(root.get("transactionId"), filters.transactionId()));
      }
      if (filters.riskLevel() != null) {
        RiskAssessmentProperties.LevelThresholds t = riskProperties.levelThresholds();
        switch (filters.riskLevel()) {
          case LOW -> predicates.add(cb.lessThanOrEqualTo(root.get(RISK_SCORE), t.lowMax()));
          case MEDIUM -> {
            predicates.add(cb.greaterThan(root.get(RISK_SCORE), t.lowMax()));
            predicates.add(cb.lessThanOrEqualTo(root.get(RISK_SCORE), t.mediumMax()));
          }
          case HIGH -> predicates.add(cb.greaterThan(root.get(RISK_SCORE), t.mediumMax()));
        }
      }
      if (filters.from() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("triggeredAt"), filters.from()));
      }
      if (filters.to() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("triggeredAt"), filters.to()));
      }
      if (filters.minScore() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get(RISK_SCORE), filters.minScore()));
      }
      if (filters.maxScore() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get(RISK_SCORE), filters.maxScore()));
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
