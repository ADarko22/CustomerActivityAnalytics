package io.github.adarko22.customeractivityanalytics.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A dedicated, bounded executor for {@code AiRiskAssessmentOrchestrator}'s background pipeline —
 * isolated from Boot's default task executor so a burst of assessment requests can't starve other
 * {@code @Async} usage.
 */
@Configuration
@EnableAsync
public class RiskAssessmentAsyncConfig {

  @Bean
  public Executor riskAssessmentExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("risk-assessment-");
    executor.initialize();
    return executor;
  }
}
