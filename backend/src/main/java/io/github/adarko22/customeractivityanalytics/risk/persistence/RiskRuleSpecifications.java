package io.github.adarko22.customeractivityanalytics.risk.persistence;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class RiskRuleSpecifications {

  private static final char LIKE_ESCAPE = '\\';

  private RiskRuleSpecifications() {}

  public static Specification<RiskRule> filter(
      RuleScope appliesTo,
      String ruleName,
      String thresholdLogic,
      BigDecimal minWeight,
      BigDecimal maxWeight) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (appliesTo != null) {
        predicates.add(cb.equal(root.get("appliesTo"), appliesTo));
      }
      if (ruleName != null && !ruleName.isBlank()) {
        predicates.add(
            cb.like(cb.lower(root.get("ruleName")), containsPattern(ruleName), LIKE_ESCAPE));
      }
      if (thresholdLogic != null && !thresholdLogic.isBlank()) {
        predicates.add(
            cb.like(
                cb.lower(root.get("thresholdLogic")),
                containsPattern(thresholdLogic),
                LIKE_ESCAPE));
      }
      if (minWeight != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("weight"), minWeight));
      }
      if (maxWeight != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("weight"), maxWeight));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Escapes {@code LIKE} wildcard metacharacters ({@code %}, {@code _}) so a filter value
   * containing them is matched literally rather than as a wildcard — paired with {@link
   * #LIKE_ESCAPE} on every {@code cb.like} call above.
   */
  private static String containsPattern(String value) {
    String escaped =
        value
            .toLowerCase()
            .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + "" + LIKE_ESCAPE)
            .replace("%", LIKE_ESCAPE + "%")
            .replace("_", LIKE_ESCAPE + "_");
    return "%" + escaped + "%";
  }
}
