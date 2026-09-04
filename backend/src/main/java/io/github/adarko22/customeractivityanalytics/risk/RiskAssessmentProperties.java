package io.github.adarko22.customeractivityanalytics.risk;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable risk-scoring/pipeline bounds — max activated rules, the model-call and SSE timeouts
 * (kept consistent per the phase's Reliability NFR), the score→{@link RiskLevel} thresholds, and
 * how many prior assessments feed the history RAG source.
 */
@ConfigurationProperties(prefix = "app.risk")
public record RiskAssessmentProperties(
    int maxTriggeredRules,
    Duration assessmentTimeout,
    Duration sseTimeout,
    LevelThresholds levelThresholds,
    int historyContextSize) {

  @PostConstruct
  void validate() {
    if (levelThresholds.mediumMax().compareTo(levelThresholds.lowMax()) <= 0) {
      throw new IllegalStateException(
          "app.risk.level-thresholds.medium-max must be greater than low-max");
    }
    if (sseTimeout.compareTo(assessmentTimeout) <= 0) {
      throw new IllegalStateException(
          "app.risk.sse-timeout must be greater than assessment-timeout, so a COMPLETE/FAILED"
              + " event always has time to land before the SSE connection times out");
    }
  }

  public RiskLevel levelFor(BigDecimal score) {
    if (score.compareTo(levelThresholds.lowMax()) <= 0) {
      return RiskLevel.LOW;
    }
    if (score.compareTo(levelThresholds.mediumMax()) <= 0) {
      return RiskLevel.MEDIUM;
    }
    return RiskLevel.HIGH;
  }

  public record LevelThresholds(BigDecimal lowMax, BigDecimal mediumMax) {}
}
