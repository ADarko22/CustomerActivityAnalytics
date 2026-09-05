package io.github.adarko22.customeractivityanalytics.risk.engine;

import io.github.adarko22.customeractivityanalytics.risk.ai.AiProviderProperties;
import io.github.adarko22.customeractivityanalytics.risk.ai.AssembledPrompt;
import io.github.adarko22.customeractivityanalytics.risk.ai.ModelAssessmentResult;
import io.github.adarko22.customeractivityanalytics.risk.ai.RiskAssessmentAiClient;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentEventDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.AssessmentStage;
import io.github.adarko22.customeractivityanalytics.risk.dto.RuleContributionDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentPersistenceService;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The SSE-driving assessment pipeline: RAG retrieval → model call → scoring → two-table
 * persistence, streaming a progress token per stage. Runs on a dedicated executor so the controller
 * thread returns immediately; persistence happens unconditionally once the model call starts, even
 * if the SSE connection drops (docs/development/PHASE_4_PLAN.md Clarification #9).
 */
@Service
public class AiRiskAssessmentOrchestrator {

  private static final Logger log = LoggerFactory.getLogger(AiRiskAssessmentOrchestrator.class);
  private static final String GENERIC_FAILURE_MESSAGE =
      "Assessment could not be completed. Please retry.";

  private final RiskRuleRetrievalService riskRuleRetrievalService;
  private final AssessmentHistoryRetrievalService assessmentHistoryRetrievalService;
  private final PromptContextMapper promptContextMapper;
  private final RiskAssessmentPromptAssembler promptAssembler;
  private final PiiGuardrailService guardrailService;
  private final RiskAssessmentAiClient aiClient;
  private final RiskScoringService riskScoringService;
  private final RiskAssessmentPersistenceService persistenceService;
  private final RiskAssessmentProperties riskProperties;
  private final AiProviderProperties aiProviderProperties;

  public AiRiskAssessmentOrchestrator(
      RiskRuleRetrievalService riskRuleRetrievalService,
      AssessmentHistoryRetrievalService assessmentHistoryRetrievalService,
      PromptContextMapper promptContextMapper,
      RiskAssessmentPromptAssembler promptAssembler,
      PiiGuardrailService guardrailService,
      RiskAssessmentAiClient aiClient,
      RiskScoringService riskScoringService,
      RiskAssessmentPersistenceService persistenceService,
      RiskAssessmentProperties riskProperties,
      AiProviderProperties aiProviderProperties) {
    this.riskRuleRetrievalService = riskRuleRetrievalService;
    this.assessmentHistoryRetrievalService = assessmentHistoryRetrievalService;
    this.promptContextMapper = promptContextMapper;
    this.promptAssembler = promptAssembler;
    this.guardrailService = guardrailService;
    this.aiClient = aiClient;
    this.riskScoringService = riskScoringService;
    this.persistenceService = persistenceService;
    this.riskProperties = riskProperties;
    this.aiProviderProperties = aiProviderProperties;
  }

  @Async("riskAssessmentExecutor")
  public void run(TransactionDto transaction, SseEmitter emitter) {
    emitSafely(emitter, AiRiskAssessmentEventDto.progress(AssessmentStage.PROMPT_BUILDING));
    String context = promptContextMapper.map(transaction);

    emitSafely(emitter, AiRiskAssessmentEventDto.progress(AssessmentStage.RULE_RETRIEVAL));
    List<RiskRule> rules = riskRuleRetrievalService.findApplicable(transaction.activityType());

    emitSafely(emitter, AiRiskAssessmentEventDto.progress(AssessmentStage.HISTORY_RETRIEVAL));
    List<RiskFinalAssessment> history =
        assessmentHistoryRetrievalService.recentFor(
            transaction.transactionId(), riskProperties.historyContextSize());

    AssembledPrompt prompt = promptAssembler.assemble(context, rules, history);

    emitSafely(emitter, AiRiskAssessmentEventDto.progress(AssessmentStage.GUARDRAIL_CHECK));
    guardrailService
        .scan(prompt.user())
        .ifPresent(
            pattern ->
                log.warn(
                    "AI risk assessment prompt matched a PII guardrail pattern (advisory only,"
                        + " assessment proceeds): transactionId={}, pattern={}",
                    transaction.transactionId(),
                    pattern));

    emitSafely(emitter, AiRiskAssessmentEventDto.progress(AssessmentStage.MODEL_CALL));
    try {
      ModelAssessmentResult result = callWithTimeout(prompt);

      Map<UUID, RiskRule> rulesById =
          rules.stream().collect(Collectors.toMap(RiskRule::getRuleId, r -> r));
      RiskScoringService.ScoredAssessment scored =
          riskScoringService.score(result.ruleMatches(), rulesById);
      RiskFinalAssessment persisted =
          persistenceService.save(
              transaction.transactionId(), scored, result.findings(), result.recommendations());

      log.info(
          "AI risk assessment completed: transactionId={}, promptVersion={}, provider={},"
              + " model={}, matchedRules={}, retainedRules={}, candidateRules={}, historyRows={},"
              + " riskLevel={}",
          transaction.transactionId(),
          RiskAssessmentAiClient.PROMPT_VERSION,
          aiProviderProperties.provider(),
          aiClient.modelName(),
          result.ruleMatches().size(),
          scored.retained().size(),
          rules.size(),
          history.size(),
          scored.level());
      log.debug(
          "AI risk assessment rule matches: transactionId={}, matches={}",
          transaction.transactionId(),
          result.ruleMatches());

      emitSafely(
          emitter,
          AiRiskAssessmentEventDto.complete(toDto(persisted, scored, rulesById, riskProperties)));
      emitter.complete();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("AI risk assessment interrupted: transactionId={}", transaction.transactionId(), e);
      emitSafely(emitter, AiRiskAssessmentEventDto.failed(GENERIC_FAILURE_MESSAGE));
      emitter.complete();
    } catch (ExecutionException | TimeoutException | RuntimeException e) {
      log.warn("AI risk assessment failed: transactionId={}", transaction.transactionId(), e);
      emitSafely(emitter, AiRiskAssessmentEventDto.failed(GENERIC_FAILURE_MESSAGE));
      emitter.complete();
    }
  }

  private ModelAssessmentResult callWithTimeout(AssembledPrompt prompt)
      throws InterruptedException, ExecutionException, TimeoutException {
    return CompletableFuture.supplyAsync(() -> aiClient.assess(prompt))
        .get(riskProperties.assessmentTimeout().toMillis(), TimeUnit.MILLISECONDS);
  }

  private static AiRiskAssessmentDto toDto(
      RiskFinalAssessment persisted,
      RiskScoringService.ScoredAssessment scored,
      Map<UUID, RiskRule> rulesById,
      RiskAssessmentProperties riskProperties) {
    List<RuleContributionDto> contributions =
        scored.retained().stream()
            .map(
                sr ->
                    new RuleContributionDto(
                        sr.ruleId(),
                        rulesById.get(sr.ruleId()).getRuleName(),
                        sr.scoreContribution()))
            .toList();
    return new AiRiskAssessmentDto(
        persisted.getAssessmentId(),
        persisted.getTransactionId(),
        persisted.getTriggeredAt(),
        riskProperties.levelFor(persisted.getRiskScore()),
        persisted.getRiskScore(),
        persisted.getFindings(),
        persisted.getRecommendations(),
        contributions);
  }

  private void emitSafely(SseEmitter emitter, AiRiskAssessmentEventDto event) {
    try {
      emitter.send(event);
    } catch (IOException e) {
      log.debug("SSE emitter closed (client likely disconnected); continuing assessment", e);
    }
  }
}
