package io.github.adarko22.customeractivityanalytics.risk;

import io.github.adarko22.customeractivityanalytics.risk.ai.RuleMatch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns the model's raw {@link RuleMatch} list into a persisted score: {@code weight × relevance}
 * per rule, capped to the top {@code app.risk.max-triggered-rules} by relevance (docs/development/
 * PHASE_4_PLAN.md Clarification #8), summed and mapped to a {@link RiskLevel}. Pure/DB-free.
 */
@Service
public class RiskScoringService {

  private static final Logger log = LoggerFactory.getLogger(RiskScoringService.class);
  private static final BigDecimal ZERO = BigDecimal.ZERO;
  private static final BigDecimal ONE = BigDecimal.ONE;

  private final RiskAssessmentProperties properties;

  public RiskScoringService(RiskAssessmentProperties properties) {
    this.properties = properties;
  }

  public ScoredAssessment score(List<RuleMatch> matches, Map<UUID, RiskRule> rulesById) {
    List<ScoredRule> candidates = new ArrayList<>();
    for (RuleMatch match : matches) {
      RiskRule rule = rulesById.get(match.ruleId());
      if (rule == null) {
        log.warn("Ignoring model-reported rule match for unknown ruleId={}", match.ruleId());
        continue;
      }
      BigDecimal relevance = clampRelevance(match.ruleId(), match.relevance());
      BigDecimal scoreContribution =
          rule.getWeight().multiply(relevance).setScale(2, RoundingMode.HALF_UP);
      candidates.add(new ScoredRule(rule.getRuleId(), relevance, scoreContribution));
    }

    List<ScoredRule> retained =
        candidates.stream()
            .sorted(Comparator.comparing(ScoredRule::relevance).reversed())
            .limit(properties.maxTriggeredRules())
            .toList();

    BigDecimal totalScore =
        retained.stream()
            .map(ScoredRule::scoreContribution)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

    return new ScoredAssessment(properties.levelFor(totalScore), totalScore, retained);
  }

  private static BigDecimal clampRelevance(UUID ruleId, BigDecimal relevance) {
    if (relevance.compareTo(ZERO) < 0 || relevance.compareTo(ONE) > 0) {
      log.warn("Clamping out-of-range relevance={} for ruleId={}", relevance, ruleId);
      return relevance.compareTo(ZERO) < 0 ? ZERO : ONE;
    }
    return relevance;
  }

  public record ScoredRule(UUID ruleId, BigDecimal relevance, BigDecimal scoreContribution) {}

  public record ScoredAssessment(
      RiskLevel level, BigDecimal totalScore, List<ScoredRule> retained) {}
}
