package io.github.adarko22.customeractivityanalytics.risk.ai;

import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link ChatClient}-backed implementation, wired to the OpenAI-shaped {@code
 * spring-ai-starter-model-openai} (whose HTTP endpoint WireMock stands in for offline — docs/
 * DECISIONS.md D4). Active whenever {@code app.ai.provider} is {@code openai} or unset (the
 * long-standing default). Injects the concrete {@link OpenAiChatModel} bean rather than the generic
 * {@code ChatClient.Builder} — with a second provider starter on the classpath, Spring AI no longer
 * auto-configures a single unqualified {@code ChatClient.Builder} (see docs/DECISIONS.md D19).
 * System/user prompts are classpath resources ("prompt engineering managed as code," Feature 5),
 * not inline strings, so they're reviewable/diffable independently of this orchestration code.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider",
    havingValue = "openai",
    matchIfMissing = true)
public class OpenAiRiskAssessmentAiClient implements RiskAssessmentAiClient {

  private final ChatClient chatClient;
  private final PromptTemplate systemPromptTemplate;
  private final PromptTemplate userPromptTemplate;
  private final String model;

  public OpenAiRiskAssessmentAiClient(
      OpenAiChatModel openAiChatModel,
      @Value("classpath:prompts/risk-assessment-system.st") Resource systemPromptResource,
      @Value("classpath:prompts/risk-assessment-user.st") Resource userPromptResource,
      @Value("${spring.ai.openai.chat.options.model}") String model) {
    this.chatClient = ChatClient.builder(openAiChatModel).build();
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
