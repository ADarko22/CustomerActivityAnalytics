package io.github.adarko22.customeractivityanalytics.risk;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.risk.ai.RuleMatch;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RiskScoringServiceTest {

  private final RiskAssessmentProperties defaultProperties = properties(5, "30", "70");

  private static RiskAssessmentProperties properties(
      int maxTriggeredRules, String lowMax, String mediumMax) {
    return new RiskAssessmentProperties(
        maxTriggeredRules,
        Duration.ofSeconds(30),
        Duration.ofSeconds(35),
        new RiskAssessmentProperties.LevelThresholds(
            new BigDecimal(lowMax), new BigDecimal(mediumMax)),
        5);
  }

  @Test
  void computesScoreContributionAsWeightTimesRelevance() {
    UUID ruleId = UUID.randomUUID();
    RiskScoringService service = new RiskScoringService(defaultProperties);

    RiskScoringService.ScoredAssessment result =
        service.score(
            List.of(new RuleMatch(ruleId, new BigDecimal("0.50"))),
            Map.of(ruleId, rule(ruleId, "20.00")));

    assertThat(result.retained()).hasSize(1);
    assertThat(result.retained().get(0).scoreContribution()).isEqualByComparingTo("10.00");
    assertThat(result.totalScore()).isEqualByComparingTo("10.00");
  }

  @Test
  void capsRetainedRulesToMaxTriggeredRulesByRelevanceDescending() {
    RiskScoringService service = new RiskScoringService(properties(2, "30", "70"));
    UUID highest = UUID.randomUUID();
    UUID middle = UUID.randomUUID();
    UUID lowest = UUID.randomUUID();
    Map<UUID, RiskRule> rulesById =
        Map.of(
            highest, rule(highest, "10.00"),
            middle, rule(middle, "10.00"),
            lowest, rule(lowest, "10.00"));

    RiskScoringService.ScoredAssessment result =
        service.score(
            List.of(
                new RuleMatch(middle, new BigDecimal("0.50")),
                new RuleMatch(highest, new BigDecimal("0.90")),
                new RuleMatch(lowest, new BigDecimal("0.10"))),
            rulesById);

    assertThat(result.retained()).hasSize(2);
    assertThat(result.retained())
        .extracting(RiskScoringService.ScoredRule::ruleId)
        .containsExactly(highest, middle);
  }

  @Test
  void mapsTotalScoreToLowMediumHighAtThresholdBoundaries() {
    RiskScoringService service = new RiskScoringService(properties(5, "30", "70"));
    UUID ruleId = UUID.randomUUID();
    Map<UUID, RiskRule> rulesById = Map.of(ruleId, rule(ruleId, "100.00"));

    RiskScoringService.ScoredAssessment atLowBoundary =
        service.score(List.of(new RuleMatch(ruleId, new BigDecimal("0.30"))), rulesById);
    assertThat(atLowBoundary.level()).isEqualTo(RiskLevel.LOW);

    RiskScoringService.ScoredAssessment justAboveLow =
        service.score(List.of(new RuleMatch(ruleId, new BigDecimal("0.31"))), rulesById);
    assertThat(justAboveLow.level()).isEqualTo(RiskLevel.MEDIUM);

    RiskScoringService.ScoredAssessment atMediumBoundary =
        service.score(List.of(new RuleMatch(ruleId, new BigDecimal("0.70"))), rulesById);
    assertThat(atMediumBoundary.level()).isEqualTo(RiskLevel.MEDIUM);

    RiskScoringService.ScoredAssessment justAboveMedium =
        service.score(List.of(new RuleMatch(ruleId, new BigDecimal("0.71"))), rulesById);
    assertThat(justAboveMedium.level()).isEqualTo(RiskLevel.HIGH);
  }

  @Test
  void clampsOutOfRangeRelevanceIntoZeroToOne() {
    RiskScoringService service = new RiskScoringService(defaultProperties);
    UUID tooHigh = UUID.randomUUID();
    UUID tooLow = UUID.randomUUID();
    Map<UUID, RiskRule> rulesById =
        Map.of(tooHigh, rule(tooHigh, "10.00"), tooLow, rule(tooLow, "10.00"));

    RiskScoringService.ScoredAssessment result =
        service.score(
            List.of(
                new RuleMatch(tooHigh, new BigDecimal("1.50")),
                new RuleMatch(tooLow, new BigDecimal("-0.20"))),
            rulesById);

    assertThat(result.retained())
        .anySatisfy(
            scored -> {
              if (scored.ruleId().equals(tooHigh)) {
                assertThat(scored.scoreContribution()).isEqualByComparingTo("10.00");
              }
            });
    assertThat(result.retained())
        .filteredOn(scored -> scored.ruleId().equals(tooLow))
        .allSatisfy(scored -> assertThat(scored.scoreContribution()).isEqualByComparingTo("0.00"));
  }

  @Test
  void ignoresMatchesForUnknownRuleId() {
    RiskScoringService service = new RiskScoringService(defaultProperties);

    RiskScoringService.ScoredAssessment result =
        service.score(List.of(new RuleMatch(UUID.randomUUID(), new BigDecimal("0.80"))), Map.of());

    assertThat(result.retained()).isEmpty();
    assertThat(result.totalScore()).isEqualByComparingTo("0.00");
    assertThat(result.level()).isEqualTo(RiskLevel.LOW);
  }

  private static RiskRule rule(UUID ruleId, String weight) {
    return new RiskRule(
        ruleId, "Rule " + ruleId, RuleScope.ALL, "condition", new BigDecimal(weight));
  }
}
