package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RiskAssessmentPropertiesTest {

  private static final String[] VALID_PROPERTIES = {
    "app.risk.max-triggered-rules=5",
    "app.risk.assessment-timeout=45s",
    "app.risk.sse-timeout=50s",
    "app.risk.level-thresholds.low-max=30",
    "app.risk.level-thresholds.medium-max=70",
    "app.risk.history-context-size=5"
  };

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withPropertyValues(VALID_PROPERTIES);

  @Test
  void bindsValuesAndComputesRiskLevelFromScore() {
    contextRunner.run(
        context -> {
          RiskAssessmentProperties properties = context.getBean(RiskAssessmentProperties.class);
          assertThat(properties.maxTriggeredRules()).isEqualTo(5);
          assertThat(properties.historyContextSize()).isEqualTo(5);
          assertThat(properties.levelFor(new BigDecimal("30"))).isEqualTo(RiskLevel.LOW);
          assertThat(properties.levelFor(new BigDecimal("31"))).isEqualTo(RiskLevel.MEDIUM);
          assertThat(properties.levelFor(new BigDecimal("70"))).isEqualTo(RiskLevel.MEDIUM);
          assertThat(properties.levelFor(new BigDecimal("71"))).isEqualTo(RiskLevel.HIGH);
        });
  }

  @Test
  void failsFastWhenMediumMaxIsNotGreaterThanLowMax() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.risk.max-triggered-rules=5",
            "app.risk.assessment-timeout=45s",
            "app.risk.sse-timeout=50s",
            "app.risk.level-thresholds.low-max=70",
            "app.risk.level-thresholds.medium-max=30",
            "app.risk.history-context-size=5")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsFastWhenSseTimeoutIsNotGreaterThanAssessmentTimeout() {
    new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "app.risk.max-triggered-rules=5",
            "app.risk.assessment-timeout=50s",
            "app.risk.sse-timeout=45s",
            "app.risk.level-thresholds.low-max=30",
            "app.risk.level-thresholds.medium-max=70",
            "app.risk.history-context-size=5")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(RiskAssessmentProperties.class)
  @Configuration
  static class TestConfig {}
}
