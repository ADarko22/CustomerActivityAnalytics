package io.github.adarko22.customeractivityanalytics.risk;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the two-table write (docs/DECISIONS.md D6) as its own Spring bean so {@code @Transactional}
 * is honored through the proxy — {@link AiRiskAssessmentOrchestrator} calling this as a same-class
 * private method would silently skip the transaction (Spring AOP self-invocation).
 */
@Service
public class RiskAssessmentPersistenceService {

  private final RiskFinalAssessmentRepository riskFinalAssessmentRepository;
  private final RiskAssessmentLineItemRepository riskAssessmentLineItemRepository;

  public RiskAssessmentPersistenceService(
      RiskFinalAssessmentRepository riskFinalAssessmentRepository,
      RiskAssessmentLineItemRepository riskAssessmentLineItemRepository) {
    this.riskFinalAssessmentRepository = riskFinalAssessmentRepository;
    this.riskAssessmentLineItemRepository = riskAssessmentLineItemRepository;
  }

  @Transactional
  public RiskFinalAssessment save(
      UUID transactionId,
      RiskScoringService.ScoredAssessment scored,
      String findings,
      String recommendations) {
    UUID assessmentId = UUID.randomUUID();
    Instant triggeredAt = Instant.now();
    RiskFinalAssessment finalAssessment =
        new RiskFinalAssessment(
            assessmentId,
            transactionId,
            triggeredAt,
            scored.level(),
            scored.totalScore(),
            findings,
            recommendations);
    riskFinalAssessmentRepository.save(finalAssessment);
    for (RiskScoringService.ScoredRule scoredRule : scored.retained()) {
      riskAssessmentLineItemRepository.save(
          new RiskAssessmentLineItem(
              assessmentId,
              scoredRule.ruleId(),
              transactionId,
              triggeredAt,
              scoredRule.scoreContribution()));
    }
    return finalAssessment;
  }
}
