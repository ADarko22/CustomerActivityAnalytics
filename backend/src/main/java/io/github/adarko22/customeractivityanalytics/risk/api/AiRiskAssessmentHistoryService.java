package io.github.adarko22.customeractivityanalytics.risk.api;

import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.RuleContributionDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentLineItemRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessmentRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessmentSpecifications;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleContributionRow;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Paginated, per-column-filterable AI risk-assessment history for a customer/transaction. */
@Service
public class AiRiskAssessmentHistoryService {

  private static final Logger log = LoggerFactory.getLogger(AiRiskAssessmentHistoryService.class);

  private final CustomerService customerService;
  private final TransactionService transactionService;
  private final RiskFinalAssessmentRepository riskFinalAssessmentRepository;
  private final RiskAssessmentLineItemRepository riskAssessmentLineItemRepository;

  public AiRiskAssessmentHistoryService(
      CustomerService customerService,
      TransactionService transactionService,
      RiskFinalAssessmentRepository riskFinalAssessmentRepository,
      RiskAssessmentLineItemRepository riskAssessmentLineItemRepository) {
    this.customerService = customerService;
    this.transactionService = transactionService;
    this.riskFinalAssessmentRepository = riskFinalAssessmentRepository;
    this.riskAssessmentLineItemRepository = riskAssessmentLineItemRepository;
  }

  public Page<AiRiskAssessmentDto> findHistory(
      UUID customerId,
      UUID transactionId,
      RiskLevel riskLevel,
      Instant from,
      Instant to,
      BigDecimal minScore,
      BigDecimal maxScore,
      Pageable pageable) {
    customerService.requireExists(customerId);
    if (transactionId != null) {
      // 404s if the transaction doesn't exist or doesn't belong to this customer.
      transactionService.findDetail(customerId, transactionId);
    }

    log.info(
        "Listing AI risk assessments: customerId={}, transactionId={}, riskLevel={}, page={},"
            + " size={}",
        customerId,
        transactionId,
        riskLevel,
        pageable.getPageNumber(),
        pageable.getPageSize());

    Page<RiskFinalAssessment> page =
        riskFinalAssessmentRepository.findAll(
            RiskFinalAssessmentSpecifications.filter(
                customerId, transactionId, riskLevel, from, to, minScore, maxScore),
            pageable);

    Map<UUID, List<RuleContributionDto>> contributionsByAssessment = contributionsFor(page);

    return page.map(
        assessment ->
            new AiRiskAssessmentDto(
                assessment.getAssessmentId(),
                assessment.getTransactionId(),
                assessment.getTriggeredAt(),
                assessment.getRiskLevel(),
                assessment.getRiskScore(),
                assessment.getFindings(),
                assessment.getRecommendations(),
                contributionsByAssessment.getOrDefault(assessment.getAssessmentId(), List.of())));
  }

  private Map<UUID, List<RuleContributionDto>> contributionsFor(Page<RiskFinalAssessment> page) {
    List<UUID> assessmentIds = page.map(RiskFinalAssessment::getAssessmentId).getContent();
    if (assessmentIds.isEmpty()) {
      return Map.of();
    }
    return riskAssessmentLineItemRepository.findByAssessmentIdInWithRuleName(assessmentIds).stream()
        .collect(
            Collectors.groupingBy(
                RuleContributionRow::assessmentId,
                Collectors.mapping(
                    row ->
                        new RuleContributionDto(
                            row.ruleId(), row.ruleName(), row.scoreContribution()),
                    Collectors.toList())));
  }
}
