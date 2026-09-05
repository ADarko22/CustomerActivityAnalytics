package io.github.adarko22.customeractivityanalytics.risk.ai;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link ChatClient}-backed implementation wired to {@code
 * spring-ai-starter-model-anthropic}. Active only when {@code app.ai.provider=anthropic} (docs/
 * DECISIONS.md D19) — structurally mirrors {@link OpenAiRiskAssessmentAiClient}. Prompt rendering
 * itself lives in {@link
 * io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentPromptAssembler}, shared
 * with {@link OpenAiRiskAssessmentAiClient} and scanned by the PII guardrail before either client
 * is ever called.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "anthropic")
public class AnthropicRiskAssessmentAiClient implements RiskAssessmentAiClient {

  private final ChatClient chatClient;
  private final String model;

  public AnthropicRiskAssessmentAiClient(
      AnthropicChatModel anthropicChatModel,
      @Value("${spring.ai.anthropic.chat.options.model}") String model) {
    this.chatClient = ChatClient.builder(anthropicChatModel).build();
    this.model = model;
  }

  @Override
  public ModelAssessmentResult assess(AssembledPrompt prompt) {
    return chatClient
        .prompt()
        .system(prompt.system())
        .user(prompt.user())
        .call()
        .entity(ModelAssessmentResult.class);
  }

  @Override
  public String modelName() {
    return model;
  }
}
