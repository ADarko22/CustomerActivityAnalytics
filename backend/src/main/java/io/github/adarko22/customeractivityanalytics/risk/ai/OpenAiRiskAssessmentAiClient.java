package io.github.adarko22.customeractivityanalytics.risk.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI {@link ChatClient}-backed implementation, wired to the OpenAI-shaped {@code
 * spring-ai-starter-model-openai} (whose HTTP endpoint WireMock stands in for offline — docs/
 * DECISIONS.md D4). Active whenever {@code app.ai.provider} is {@code openai} or unset (the
 * long-standing default). Injects the concrete {@link OpenAiChatModel} bean rather than the generic
 * {@code ChatClient.Builder} — with a second provider starter on the classpath, Spring AI no longer
 * auto-configures a single unqualified {@code ChatClient.Builder} (see docs/DECISIONS.md D19).
 * Prompt rendering itself lives in {@link
 * io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentPromptAssembler}, shared
 * with {@link AnthropicRiskAssessmentAiClient} and scanned by the PII guardrail before either
 * client is ever called.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider",
    havingValue = "openai",
    matchIfMissing = true)
public class OpenAiRiskAssessmentAiClient implements RiskAssessmentAiClient {

  private final ChatClient chatClient;
  private final String model;

  public OpenAiRiskAssessmentAiClient(
      OpenAiChatModel openAiChatModel,
      @Value("${spring.ai.openai.chat.options.model}") String model) {
    this.chatClient = ChatClient.builder(openAiChatModel).build();
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
