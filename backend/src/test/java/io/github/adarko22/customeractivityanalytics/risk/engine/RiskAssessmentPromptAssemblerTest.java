package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.risk.ai.AssembledPrompt;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RiskAssessmentPromptAssemblerTest {

  private static RiskAssessmentPromptAssembler assembler(RiskAssessmentProperties properties) {
    return new RiskAssessmentPromptAssembler(
        new ClassPathResource("prompts/risk-assessment-system.st"),
        new ClassPathResource("prompts/risk-assessment-user.st"),
        properties);
  }

  private static RiskAssessmentProperties properties(BigDecimal lowMax, BigDecimal mediumMax) {
    return new RiskAssessmentProperties(
        5,
        Duration.ofSeconds(45),
        Duration.ofSeconds(50),
        new RiskAssessmentProperties.LevelThresholds(lowMax, mediumMax),
        5);
  }

  @Test
  void assembleRendersStaticSystemPromptAndInterpolatedUserPrompt() {
    RiskAssessmentPromptAssembler assembler =
        assembler(properties(new BigDecimal("30"), new BigDecimal("70")));
    RiskRule rule =
        new RiskRule(
            UUID.randomUUID(),
            "High-value transaction",
            RuleScope.ALL,
            "amount > 5000",
            new BigDecimal("25.00"));

    AssembledPrompt prompt =
        assembler.assemble("transaction context here", List.of(rule), List.of());

    assertThat(prompt.system()).contains("financial-crime risk analyst");
    assertThat(prompt.user()).contains("transaction context here");
    assertThat(prompt.user()).contains("High-value transaction");
    assertThat(prompt.user()).contains("(no prior assessments for this transaction)");
  }

  @Test
  void assembleRendersNoCandidateRulesPlaceholderWhenRulesAreEmpty() {
    RiskAssessmentPromptAssembler assembler =
        assembler(properties(new BigDecimal("30"), new BigDecimal("70")));

    AssembledPrompt prompt = assembler.assemble("context", List.of(), List.of());

    assertThat(prompt.user())
        .contains("(no candidate rules apply to this transaction's activity type)");
  }

  @Test
  void assembleRendersHistoryEntryRiskLevelFromCurrentThresholds() {
    RiskFinalAssessment history =
        new RiskFinalAssessment(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            new BigDecimal("50.00"),
            "prior findings",
            "prior recommendations");

    AssembledPrompt underLenientThresholds =
        assembler(properties(new BigDecimal("60"), new BigDecimal("90")))
            .assemble("context", List.of(), List.of(history));
    AssembledPrompt underStrictThresholds =
        assembler(properties(new BigDecimal("10"), new BigDecimal("40")))
            .assemble("context", List.of(), List.of(history));

    assertThat(underLenientThresholds.user()).contains("riskLevel: LOW");
    assertThat(underStrictThresholds.user()).contains("riskLevel: HIGH");
  }
}
