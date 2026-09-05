package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PiiGuardrailPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  void bindsValidPatternsSuccessfully() {
    contextRunner
        .withPropertyValues(
            "app.risk.guardrail.patterns[0].name=CARD_PAN",
            "app.risk.guardrail.patterns[0].regex=\\\\d{13,19}")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              PiiGuardrailProperties properties = context.getBean(PiiGuardrailProperties.class);
              assertThat(properties.patterns()).hasSize(1);
              assertThat(properties.patterns().get(0).name()).isEqualTo("CARD_PAN");
            });
  }

  @Test
  void failsFastWhenNoPatternsConfigured() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsFastWhenPatternNameIsBlank() {
    contextRunner
        .withPropertyValues(
            "app.risk.guardrail.patterns[0].name=", "app.risk.guardrail.patterns[0].regex=\\\\d+")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsFastWhenPatternRegexIsBlank() {
    contextRunner
        .withPropertyValues(
            "app.risk.guardrail.patterns[0].name=CARD_PAN", "app.risk.guardrail.patterns[0].regex=")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void failsFastWhenPatternRegexIsInvalid() {
    contextRunner
        .withPropertyValues(
            "app.risk.guardrail.patterns[0].name=CARD_PAN",
            "app.risk.guardrail.patterns[0].regex=[unclosed")
        .run(context -> assertThat(context).hasFailed());
  }

  @EnableConfigurationProperties(PiiGuardrailProperties.class)
  @Configuration
  static class TestConfig {}
}
