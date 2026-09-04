package io.github.adarko22.customeractivityanalytics.risk;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.risk.ai.ModelAssessmentResult;
import io.github.adarko22.customeractivityanalytics.risk.ai.RuleMatch;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Exercises the full pipeline (RAG retrieval → model call → scoring → persistence) against a
 * WireMock-stubbed OpenAI-shaped response, so the deserialization/scoring code that also serves the
 * offline demo (docs/DECISIONS.md D4) is verified end to end, not just mocked at the {@link
 * io.github.adarko22.customeractivityanalytics.risk.ai.RiskAssessmentAiClient} boundary.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AiRiskAssessmentWireMockReplayTest extends AbstractPostgresIntegrationTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void aiProviderProperties(DynamicPropertyRegistry registry) {
    // The openai-java SDK's default base-url is "https://api.openai.com/v1" (path included), so a
    // full override must carry the /v1 suffix too for the resulting request path to match.
    registry.add("spring.ai.openai.base-url", () -> wireMock.baseUrl() + "/v1");
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CardActivityRepository cardActivityRepository;
  @Autowired private RiskRuleRepository riskRuleRepository;
  @Autowired private RiskFinalAssessmentRepository riskFinalAssessmentRepository;
  @Autowired private AiRiskAssessmentOrchestrator orchestrator;

  private UUID transactionId;
  private UUID ruleId;

  @BeforeEach
  void setUp() {
    UUID customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
    transactionId = UUID.randomUUID();
    cardActivityRepository.save(
        new CardActivity(
            transactionId,
            customerId,
            new BigDecimal("6000.00"),
            "EUR",
            TransactionStatus.COMPLETED,
            Instant.now(),
            "****1234",
            "DEBIT",
            "Amazon",
            "5732",
            false,
            "AUTH1",
            null));
    ruleId = UUID.randomUUID();
    riskRuleRepository.save(
        new RiskRule(
            ruleId,
            "High-value transaction",
            RuleScope.ALL,
            "amount > 5000",
            new BigDecimal("25.00")));
  }

  @Test
  void fullPipelineParsesAndPersistsTheWireMockStubbedResponse() throws Exception {
    stubOpenAiChatCompletion();
    TransactionDto transaction =
        TransactionMapper.toDto(cardActivityRepository.findById(transactionId).orElseThrow());

    // emitter.onCompletion()/onError() only fire via real Servlet async machinery, which this
    // webEnvironment=NONE test has none of — override complete()/completeWithError() directly
    // instead, since those are exactly what the orchestrator calls when the pipeline finishes.
    CountDownLatch latch = new CountDownLatch(1);
    SseEmitter emitter =
        new SseEmitter() {
          @Override
          public void complete() {
            super.complete();
            latch.countDown();
          }

          @Override
          public void completeWithError(Throwable ex) {
            super.completeWithError(ex);
            latch.countDown();
          }
        };

    orchestrator.run(transaction, emitter);

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

    List<RiskFinalAssessment> persisted =
        riskFinalAssessmentRepository.findByTransactionIdOrderByTriggeredAtDesc(
            transactionId, org.springframework.data.domain.PageRequest.of(0, 10));
    assertThat(persisted).hasSize(1);
    // riskScore = rule weight (25.00) x model relevance (0.75) = 18.75, within the default
    // app.risk.level-thresholds.low-max (30) configured in application.yml.
    assertThat(persisted.get(0).getRiskLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(persisted.get(0).getRiskScore()).isEqualByComparingTo("18.75");
    assertThat(persisted.get(0).getFindings()).isEqualTo("Stub findings from WireMock");
  }

  private void stubOpenAiChatCompletion() throws Exception {
    ModelAssessmentResult modelResult =
        new ModelAssessmentResult(
            List.of(new RuleMatch(ruleId, new BigDecimal("0.75"))),
            "Stub findings from WireMock",
            "Stub recommendations from WireMock");
    String innerContent = MAPPER.writeValueAsString(modelResult);
    Map<String, Object> envelope =
        Map.of(
            "id", "chatcmpl-test",
            "object", "chat.completion",
            "model", "gpt-4o-mini",
            "choices",
                List.of(
                    Map.of(
                        "index",
                        0,
                        "message",
                        Map.of("role", "assistant", "content", innerContent),
                        "finish_reason",
                        "stop")));
    String body = MAPPER.writeValueAsString(envelope);

    wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions")).willReturn(okJson(body)));
  }
}
