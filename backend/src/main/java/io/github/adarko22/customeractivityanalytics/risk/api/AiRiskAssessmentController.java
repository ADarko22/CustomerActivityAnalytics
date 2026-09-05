package io.github.adarko22.customeractivityanalytics.risk.api;

import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentDto;
import io.github.adarko22.customeractivityanalytics.risk.engine.AiRiskAssessmentOrchestrator;
import io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentProperties;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentHistoryFilters;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionService;
import io.github.adarko22.customeractivityanalytics.transaction.dto.TransactionDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Triggers a live-streamed AI risk assessment for a transaction and lists its assessment history.
 */
@RestController
public class AiRiskAssessmentController {

  private static final Logger log = LoggerFactory.getLogger(AiRiskAssessmentController.class);

  private final TransactionService transactionService;
  private final AiRiskAssessmentOrchestrator orchestrator;
  private final AiRiskAssessmentHistoryService historyService;
  private final RiskAssessmentProperties riskProperties;

  public AiRiskAssessmentController(
      TransactionService transactionService,
      AiRiskAssessmentOrchestrator orchestrator,
      AiRiskAssessmentHistoryService historyService,
      RiskAssessmentProperties riskProperties) {
    this.transactionService = transactionService;
    this.orchestrator = orchestrator;
    this.historyService = historyService;
    this.riskProperties = riskProperties;
  }

  @GetMapping("/api/v1/customers/{customerId}/ai-assessments/stream")
  public SseEmitter stream(@PathVariable UUID customerId, @RequestParam UUID transactionId) {
    // Validate before creating the emitter — fail fast, no dangling SSE connection on a bad ID.
    TransactionDto transaction = transactionService.findDetail(customerId, transactionId);

    SseEmitter emitter = new SseEmitter(riskProperties.sseTimeout().toMillis());
    emitter.onTimeout(
        () ->
            log.debug(
                "SSE connection timed out: customerId={}, transactionId={}",
                customerId,
                transactionId));
    emitter.onError(
        e ->
            log.debug(
                "SSE connection error: customerId={}, transactionId={}",
                customerId,
                transactionId,
                e));
    emitter.onCompletion(
        () ->
            log.debug(
                "SSE connection completed: customerId={}, transactionId={}",
                customerId,
                transactionId));

    log.info(
        "Starting AI risk assessment: customerId={}, transactionId={}", customerId, transactionId);
    orchestrator.run(transaction, emitter);
    return emitter;
  }

  @GetMapping("/api/v1/customers/{customerId}/ai-assessments")
  public Page<AiRiskAssessmentDto> findHistory(
      @PathVariable UUID customerId,
      @RequestParam(required = false) UUID transactionId,
      @RequestParam(required = false) RiskLevel riskLevel,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) BigDecimal minScore,
      @RequestParam(required = false) BigDecimal maxScore,
      @PageableDefault(size = 10, sort = "triggeredAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    RiskAssessmentHistoryFilters filters =
        new RiskAssessmentHistoryFilters(transactionId, riskLevel, from, to, minScore, maxScore);
    return historyService.findHistory(customerId, filters, pageable);
  }
}
