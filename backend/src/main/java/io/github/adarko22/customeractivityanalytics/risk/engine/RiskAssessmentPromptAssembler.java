package io.github.adarko22.customeractivityanalytics.risk.engine;

import io.github.adarko22.customeractivityanalytics.risk.ai.AssembledPrompt;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Single place that renders "the fully-assembled prompt" for one assessment run, shared by both
 * {@link io.github.adarko22.customeractivityanalytics.risk.ai.RiskAssessmentAiClient}
 * implementations — replaces the old per-client duplicated rendering (formerly {@code
 * RiskPromptRenderer}) so the PII guardrail has exactly one string to scan before any model call.
 */
@Component
public class RiskAssessmentPromptAssembler {

  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final RiskAssessmentProperties riskProperties;

  public RiskAssessmentPromptAssembler(
      @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
      @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource,
      RiskAssessmentProperties riskProperties) {
    this.systemPromptTemplate = new PromptTemplate(systemPromptResource);
    this.userPromptTemplate = new PromptTemplate(userPromptResource);
    this.riskProperties = riskProperties;
  }

  public AssembledPrompt assemble(
      String transactionContext, List<RiskRule> rules, List<RiskFinalAssessment> history) {
    String user =
        userPromptTemplate.render(
            Map.of(
                "transactionContext", transactionContext,
                "rules", renderRules(rules),
                "history", renderHistory(history)));
    return new AssembledPrompt(systemPromptTemplate.render(), user);
  }

  private static String renderRules(List<RiskRule> rules) {
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

  private String renderHistory(List<RiskFinalAssessment> history) {
    if (history.isEmpty()) {
      return "(no prior assessments for this transaction)";
    }
    StringBuilder sb = new StringBuilder();
    for (RiskFinalAssessment assessment : history) {
      sb.append("- triggeredAt: ").append(assessment.getTriggeredAt()).append('\n');
      sb.append("  riskLevel: ")
          .append(riskProperties.levelFor(assessment.getRiskScore()))
          .append('\n');
      sb.append("  riskScore: ").append(assessment.getRiskScore()).append('\n');
      sb.append("  findings: ").append(assessment.getFindings()).append('\n');
    }
    return sb.toString();
  }
}
