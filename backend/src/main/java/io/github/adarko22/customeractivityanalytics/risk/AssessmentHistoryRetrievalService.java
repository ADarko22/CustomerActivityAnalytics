package io.github.adarko22.customeractivityanalytics.risk;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * RAG source #2 — the transaction's own prior {@link RiskFinalAssessment} runs, most recent first,
 * capped by {@code app.risk.history-context-size}.
 */
@Service
public class AssessmentHistoryRetrievalService {

  private final RiskFinalAssessmentRepository riskFinalAssessmentRepository;

  public AssessmentHistoryRetrievalService(
      RiskFinalAssessmentRepository riskFinalAssessmentRepository) {
    this.riskFinalAssessmentRepository = riskFinalAssessmentRepository;
  }

  public List<RiskFinalAssessment> recentFor(UUID transactionId, int limit) {
    return riskFinalAssessmentRepository.findByTransactionIdOrderByTriggeredAtDesc(
        transactionId, PageRequest.of(0, limit));
  }
}
