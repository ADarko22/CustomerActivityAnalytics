package io.github.adarko22.customeractivityanalytics.risk.ai;

import io.github.adarko22.customeractivityanalytics.risk.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.RiskRule;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link ChatClient}-backed implementation, currently wired to the OpenAI-shaped {@code
 * spring-ai-starter-model-openai} (whose HTTP endpoint WireMock stands in for offline — docs/
 * DECISIONS.md D4). System/user prompts are classpath resources ("prompt engineering managed as
 * code," Feature 5), not inline strings, so they're reviewable/diffable independently of this
 * orchestration code.
 */
@Component
public class OpenAiRiskAssessmentAiClient implements RiskAssessmentAiClient {

  private final ChatClient chatClient;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;

  public OpenAiRiskAssessmentAiClient(
      ChatClient.Builder chatClientBuilder,
      @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
      @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource) {
    this.chatClient = chatClientBuilder.build();
    this.systemPromptTemplate = new PromptTemplate(systemPromptResource);
    this.userPromptTemplate = new PromptTemplate(userPromptResource);
  }

  @Override
  public ModelAssessmentResult assess(
      String transactionContext, List<RiskRule> candidateRules, List<RiskFinalAssessment> history) {
    String userPrompt =
        userPromptTemplate.render(
            Map.of(
                "transactionContext", transactionContext,
                "rules", renderRules(candidateRules),
                "history", renderHistory(history)));
    return chatClient
        .prompt()
        .system(systemPromptTemplate.render())
        .user(userPrompt)
        .call()
        .entity(ModelAssessmentResult.class);
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

  private static String renderHistory(List<RiskFinalAssessment> history) {
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
