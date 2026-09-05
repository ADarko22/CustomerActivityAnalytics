package io.github.adarko22.customeractivityanalytics.risk.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.risk.ai.AiProviderProperties;
import io.github.adarko22.customeractivityanalytics.risk.ai.AssembledPrompt;
import io.github.adarko22.customeractivityanalytics.risk.ai.ModelAssessmentResult;
import io.github.adarko22.customeractivityanalytics.risk.ai.RiskAssessmentAiClient;
import io.github.adarko22.customeractivityanalytics.risk.ai.RuleMatch;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentEventDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.AssessmentStage;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentPersistenceService;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.dto.CardTransactionDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class AiRiskAssessmentOrchestratorTest {

  @Mock private RiskRuleRetrievalService riskRuleRetrievalService;
  @Mock private AssessmentHistoryRetrievalService assessmentHistoryRetrievalService;
  @Mock private PromptContextMapper promptContextMapper;
  @Mock private RiskAssessmentPromptAssembler promptAssembler;
  @Mock private PiiGuardrailService guardrailService;
  @Mock private RiskAssessmentAiClient aiClient;
  @Mock private RiskAssessmentPersistenceService persistenceService;
  @Mock private SseEmitter emitter;

  private final UUID transactionId = UUID.randomUUID();
  private final UUID ruleId = UUID.randomUUID();
  private static final AssembledPrompt PROMPT = new AssembledPrompt("system", "user prompt");

  private RiskAssessmentProperties properties;
  private AiRiskAssessmentOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    properties = properties(Duration.ofSeconds(2), Duration.ofSeconds(3));
    orchestrator = newOrchestrator(properties);
  }

  private AiRiskAssessmentOrchestrator newOrchestrator(RiskAssessmentProperties props) {
    return new AiRiskAssessmentOrchestrator(
        riskRuleRetrievalService,
        assessmentHistoryRetrievalService,
        promptContextMapper,
        promptAssembler,
        guardrailService,
        aiClient,
        new RiskScoringService(props),
        persistenceService,
        props,
        new AiProviderProperties("openai", false));
  }

  private static RiskAssessmentProperties properties(
      Duration assessmentTimeout, Duration sseTimeout) {
    return new RiskAssessmentProperties(
        5,
        assessmentTimeout,
        sseTimeout,
        new RiskAssessmentProperties.LevelThresholds(new BigDecimal("30"), new BigDecimal("70")),
        5);
  }

  private CardTransactionDto transactionDto() {
    return new CardTransactionDto(
        transactionId,
        UUID.randomUUID(),
        ActivityType.CARD,
        new BigDecimal("10.00"),
        "EUR",
        TransactionStatus.COMPLETED,
        Instant.now(),
        "****1234",
        "DEBIT",
        "Amazon",
        "5732",
        true,
        "AUTH1",
        null);
  }

  private void stubUpToGuardrail() {
    RiskRule rule =
        new RiskRule(ruleId, "Rule", RuleScope.ALL, "condition", new BigDecimal("20.00"));
    when(promptContextMapper.map(any())).thenReturn("context");
    when(riskRuleRetrievalService.findApplicable(ActivityType.CARD)).thenReturn(List.of(rule));
    when(assessmentHistoryRetrievalService.recentFor(eq(transactionId), anyInt()))
        .thenReturn(List.of());
    when(promptAssembler.assemble(eq("context"), any(), any())).thenReturn(PROMPT);
  }

  private void stubHappyPath() {
    stubUpToGuardrail();
    when(guardrailService.scan(PROMPT.user())).thenReturn(Optional.empty());
    when(aiClient.modelName()).thenReturn("gpt-4o-mini");
    when(aiClient.assess(PROMPT))
        .thenReturn(
            new ModelAssessmentResult(
                List.of(new RuleMatch(ruleId, new BigDecimal("0.50"))),
                "findings",
                "recommendations"));
    RiskFinalAssessment persisted =
        new RiskFinalAssessment(
            UUID.randomUUID(),
            transactionId,
            Instant.now(),
            new BigDecimal("10.00"),
            "findings",
            "recommendations");
    when(persistenceService.save(eq(transactionId), any(), eq("findings"), eq("recommendations")))
        .thenReturn(persisted);
  }

  @Test
  void emitsStagesInOrderThenCompletesWithPersistedResult() throws IOException {
    stubHappyPath();

    orchestrator.run(transactionDto(), emitter);

    ArgumentCaptor<AiRiskAssessmentEventDto> captor =
        ArgumentCaptor.forClass(AiRiskAssessmentEventDto.class);
    verify(emitter, times(6)).send(captor.capture());
    List<AssessmentStage> stages =
        captor.getAllValues().stream().map(AiRiskAssessmentEventDto::stage).toList();
    assertThat(stages)
        .containsExactly(
            AssessmentStage.PROMPT_BUILDING,
            AssessmentStage.RULE_RETRIEVAL,
            AssessmentStage.HISTORY_RETRIEVAL,
            AssessmentStage.GUARDRAIL_CHECK,
            AssessmentStage.MODEL_CALL,
            AssessmentStage.COMPLETE);
    assertThat(captor.getAllValues().get(5).result()).isNotNull();
    verify(emitter).complete();
    verify(persistenceService)
        .save(eq(transactionId), any(), eq("findings"), eq("recommendations"));
  }

  @Test
  void assessmentPersistsEvenWhenEmitterThrowsIOExceptionMidStream() throws IOException {
    stubHappyPath();
    doThrow(new IOException("client disconnected")).when(emitter).send(any(Object.class));

    orchestrator.run(transactionDto(), emitter);

    verify(persistenceService)
        .save(eq(transactionId), any(), eq("findings"), eq("recommendations"));
    verify(emitter).complete();
  }

  @Test
  void modelCallTimeoutEmitsFailedWithoutPersisting() throws IOException {
    stubUpToGuardrail();
    when(guardrailService.scan(PROMPT.user())).thenReturn(Optional.empty());
    when(aiClient.assess(PROMPT))
        .thenAnswer(
            invocation -> {
              Thread.sleep(300);
              return new ModelAssessmentResult(List.of(), "findings", "recommendations");
            });
    RiskAssessmentProperties shortTimeout =
        properties(Duration.ofMillis(50), Duration.ofMillis(500));
    AiRiskAssessmentOrchestrator timeoutOrchestrator = newOrchestrator(shortTimeout);

    timeoutOrchestrator.run(transactionDto(), emitter);

    verify(persistenceService, never()).save(any(), any(), any(), any());
    ArgumentCaptor<AiRiskAssessmentEventDto> captor =
        ArgumentCaptor.forClass(AiRiskAssessmentEventDto.class);
    verify(emitter, times(6)).send(captor.capture());
    assertThat(captor.getAllValues().get(5).stage()).isEqualTo(AssessmentStage.FAILED);
    verify(emitter).complete();
  }

  @Test
  void modelCallExceptionEmitsFailedWithoutPersisting() throws IOException {
    stubUpToGuardrail();
    when(guardrailService.scan(PROMPT.user())).thenReturn(Optional.empty());
    when(aiClient.assess(PROMPT)).thenThrow(new RuntimeException("boom"));

    orchestrator.run(transactionDto(), emitter);

    verify(persistenceService, never()).save(any(), any(), any(), any());
    ArgumentCaptor<AiRiskAssessmentEventDto> captor =
        ArgumentCaptor.forClass(AiRiskAssessmentEventDto.class);
    verify(emitter, times(6)).send(captor.capture());
    assertThat(captor.getAllValues().get(5).stage()).isEqualTo(AssessmentStage.FAILED);
  }

  @Test
  void guardrailMatchIsAdvisoryOnlyAndDoesNotBlockTheAssessment() throws IOException {
    stubHappyPath();
    when(guardrailService.scan(PROMPT.user())).thenReturn(Optional.of("CARD_PAN"));

    orchestrator.run(transactionDto(), emitter);

    verify(aiClient).assess(PROMPT);
    verify(persistenceService)
        .save(eq(transactionId), any(), eq("findings"), eq("recommendations"));
    ArgumentCaptor<AiRiskAssessmentEventDto> captor =
        ArgumentCaptor.forClass(AiRiskAssessmentEventDto.class);
    verify(emitter, times(6)).send(captor.capture());
    List<AssessmentStage> stages =
        captor.getAllValues().stream().map(AiRiskAssessmentEventDto::stage).toList();
    assertThat(stages)
        .containsExactly(
            AssessmentStage.PROMPT_BUILDING,
            AssessmentStage.RULE_RETRIEVAL,
            AssessmentStage.HISTORY_RETRIEVAL,
            AssessmentStage.GUARDRAIL_CHECK,
            AssessmentStage.MODEL_CALL,
            AssessmentStage.COMPLETE);
    verify(emitter).complete();
  }
}
