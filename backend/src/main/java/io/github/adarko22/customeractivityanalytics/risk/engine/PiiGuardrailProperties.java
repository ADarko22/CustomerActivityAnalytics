package io.github.adarko22.customeractivityanalytics.risk.engine;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-driven PII pattern definitions scanned into the fully-assembled prompt by {@link
 * PiiGuardrailService} — a new pattern is a configuration change, not a code change (docs/
 * development/PHASE_5_EXT_2.md). Fail-fast validated at startup, mirroring the existing {@code
 * AnalyticsRangeProperties}/{@link RiskAssessmentProperties} idiom.
 */
@ConfigurationProperties(prefix = "app.risk.guardrail")
public record PiiGuardrailProperties(List<PatternRule> patterns) {

  @PostConstruct
  void validate() {
    if (patterns == null || patterns.isEmpty()) {
      throw new IllegalStateException(
          "app.risk.guardrail.patterns must configure at least one PII pattern");
    }
    for (PatternRule rule : patterns) {
      if (rule.name() == null || rule.name().isBlank()) {
        throw new IllegalStateException("app.risk.guardrail.patterns[].name must not be blank");
      }
      if (rule.regex() == null || rule.regex().isBlank()) {
        throw new IllegalStateException(
            "app.risk.guardrail.patterns[].regex must not be blank for pattern=" + rule.name());
      }
      try {
        Pattern.compile(rule.regex());
      } catch (PatternSyntaxException e) {
        throw new IllegalStateException(
            "app.risk.guardrail.patterns[].regex is invalid for pattern=" + rule.name(), e);
      }
    }
  }

  public record PatternRule(String name, String regex) {}
}
