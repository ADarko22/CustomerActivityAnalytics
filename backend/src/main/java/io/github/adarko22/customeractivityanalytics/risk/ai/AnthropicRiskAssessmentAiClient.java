package io.github.adarko22.customeractivityanalytics.risk.ai;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import java.util.List;
import java.util.Map;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link ChatClient}-backed implementation wired to {@code
 * spring-ai-starter-model-anthropic}. Active only when {@code app.ai.provider=anthropic} (docs/
 * DECISIONS.md D19) — structurally mirrors {@link OpenAiRiskAssessmentAiClient}, sharing the same
 * provider-agnostic prompt templates and {@link RiskPromptRenderer} rules/history formatting.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "anthropic")
public class AnthropicRiskAssessmentAiClient implements RiskAssessmentAiClient {

  private final ChatClient chatClient;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final String model;

  public AnthropicRiskAssessmentAiClient(
      AnthropicChatModel anthropicChatModel,
      @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
      @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource,
      @Value("${spring.ai.anthropic.chat.options.model}") String model) {
    this.chatClient = ChatClient.builder(anthropicChatModel).build();
    this.systemPromptTemplate = new PromptTemplate(systemPromptResource);
    this.userPromptTemplate = new PromptTemplate(userPromptResource);
    this.model = model;
  }

  @Override
  public ModelAssessmentResult assess(
      String transactionContext, List<RiskRule> candidateRules, List<RiskFinalAssessment> history) {
    String userPrompt =
        userPromptTemplate.render(
            Map.of(
                "transactionContext", transactionContext,
                "rules", RiskPromptRenderer.renderRules(candidateRules),
                "history", RiskPromptRenderer.renderHistory(history)));
    return chatClient
        .prompt()
        .system(systemPromptTemplate.render())
        .user(userPrompt)
        .call()
        .entity(ModelAssessmentResult.class);
  }

  @Override
  public String modelName() {
    return model;
  }
}
