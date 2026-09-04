package io.github.adarko22.customeractivityanalytics.risk.ai;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import java.util.List;

/**
 * Renders the candidate rules / prior-assessment history blocks shared by every {@link
 * RiskAssessmentAiClient} implementation's user prompt — extracted so provider-specific clients
 * don't duplicate this formatting.
 */
final class RiskPromptRenderer {

  private RiskPromptRenderer() {}

  static String renderRules(List<RiskRule> rules) {
    if (rules.isEmpty()) {
      return "(no candidate rules apply to this transaction's activity type)";
    }
    StringBuilder sb = new StringBuilder();
    for (RiskRule rule : rules) {
      sb.append("- id: ").append(rule.getRuleId()).append('\n');
      sb.append("  name: ").append(rule.getRuleName()).append('\n');
      sb.append("  appliesTo: ").append(rule.getAppliesTo()).append('\n');
      sb.append("  weight: ").append(rule.getWeight()).append('\n');
      sb.append("  condition: ").append(rule.getThresholdLogic()).append('\n');
    }
    return sb.toString();
  }

  static String renderHistory(List<RiskFinalAssessment> history) {
    if (history.isEmpty()) {
      return "(no prior assessments for this transaction)";
    }
    StringBuilder sb = new StringBuilder();
    for (RiskFinalAssessment assessment : history) {
      sb.append("- triggeredAt: ").append(assessment.getTriggeredAt()).append('\n');
      sb.append("  riskLevel: ").append(assessment.getRiskLevel()).append('\n');
      sb.append("  riskScore: ").append(assessment.getRiskScore()).append('\n');
      sb.append("  findings: ").append(assessment.getFindings()).append('\n');
    }
    return sb.toString();
  }
}
