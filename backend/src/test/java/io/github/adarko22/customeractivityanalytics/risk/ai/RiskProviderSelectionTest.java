package io.github.adarko22.customeractivityanalytics.risk.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Confirms {@code app.ai.provider} genuinely selects which {@link RiskAssessmentAiClient} bean is
 * active (docs/DECISIONS.md D19) — exactly one bean either way, matching the default when unset.
 * Deliberately a slice ({@link ApplicationContextRunner}), not a full {@code @SpringBootTest}: only
 * the two provider clients and the Spring AI chat auto-configurations they depend on are under
 * test.
 */
class RiskProviderSelectionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ToolCallingAutoConfiguration.class,
                  OpenAiChatAutoConfiguration.class,
                  AnthropicChatAutoConfiguration.class))
          .withUserConfiguration(
              OpenAiRiskAssessmentAiClient.class, AnthropicRiskAssessmentAiClient.class)
          .withPropertyValues(
              "spring.ai.openai.api-key=test-key",
              "spring.ai.openai.chat.options.model=gpt-4o-mini",
              "spring.ai.anthropic.api-key=test-key",
              "spring.ai.anthropic.chat.options.model=claude-sonnet-4-5");

  @Test
  void defaultsToOpenAiWhenProviderUnset() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(RiskAssessmentAiClient.class);
          assertThat(context).hasSingleBean(OpenAiRiskAssessmentAiClient.class);
          assertThat(context).doesNotHaveBean(AnthropicRiskAssessmentAiClient.class);
        });
  }

  @Test
  void selectsOpenAiWhenProviderIsOpenai() {
    contextRunner
        .withPropertyValues("app.ai.provider=openai")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RiskAssessmentAiClient.class);
              assertThat(context).hasSingleBean(OpenAiRiskAssessmentAiClient.class);
              assertThat(context).doesNotHaveBean(AnthropicRiskAssessmentAiClient.class);
            });
  }

  @Test
  void selectsAnthropicWhenProviderIsAnthropic() {
    contextRunner
        .withPropertyValues("app.ai.provider=anthropic")
        .run(
            context -> {
              assertThat(context).hasSingleBean(RiskAssessmentAiClient.class);
              assertThat(context).hasSingleBean(AnthropicRiskAssessmentAiClient.class);
              assertThat(context).doesNotHaveBean(OpenAiRiskAssessmentAiClient.class);
            });
  }
}
