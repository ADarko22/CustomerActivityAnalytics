package io.github.adarko22.customeractivityanalytics.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.customer.CustomerService;
import io.github.adarko22.customeractivityanalytics.risk.dto.AiRiskAssessmentDto;
import io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentProperties;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentHistoryFilters;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskAssessmentLineItemRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessment;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskFinalAssessmentRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskLevel;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Regression coverage for AC5's history-endpoint half (docs/development/PHASE_5_EXT_2.md): the same
 * persisted {@code risk_score} must yield a different computed {@code riskLevel} when {@code
 * app.risk.level-thresholds} changes, proving the level is derived on every read, never frozen at
 * insert time.
 */
@ExtendWith(MockitoExtension.class)
class AiRiskAssessmentHistoryServiceTest {

  @Mock private CustomerService customerService;
  @Mock private TransactionService transactionService;
  @Mock private RiskFinalAssessmentRepository riskFinalAssessmentRepository;
  @Mock private RiskAssessmentLineItemRepository riskAssessmentLineItemRepository;

  private final UUID customerId = UUID.randomUUID();
  private final UUID transactionId = UUID.randomUUID();
  private RiskFinalAssessment persistedAssessment;

  @BeforeEach
  void setUp() {
    persistedAssessment =
        new RiskFinalAssessment(
            UUID.randomUUID(),
            transactionId,
            Instant.now(),
            new BigDecimal("50.00"),
            "findings",
            "recommendations");
    Page<RiskFinalAssessment> page =
        new PageImpl<>(List.of(persistedAssessment), PageRequest.of(0, 10), 1);
    when(riskFinalAssessmentRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(riskAssessmentLineItemRepository.findByAssessmentIdInWithRuleName(any()))
        .thenReturn(List.of());
  }

  private static RiskAssessmentProperties properties(BigDecimal lowMax, BigDecimal mediumMax) {
    return new RiskAssessmentProperties(
        5,
        Duration.ofSeconds(45),
        Duration.ofSeconds(50),
        new RiskAssessmentProperties.LevelThresholds(lowMax, mediumMax),
        5);
  }

  @Test
  void computesRiskLevelFromCurrentThresholdsNotAtInsertTime() {
    AiRiskAssessmentHistoryService lenientService =
        new AiRiskAssessmentHistoryService(
            customerService,
            transactionService,
            riskFinalAssessmentRepository,
            riskAssessmentLineItemRepository,
            properties(new BigDecimal("60"), new BigDecimal("90")));
    AiRiskAssessmentHistoryService strictService =
        new AiRiskAssessmentHistoryService(
            customerService,
            transactionService,
            riskFinalAssessmentRepository,
            riskAssessmentLineItemRepository,
            properties(new BigDecimal("10"), new BigDecimal("40")));

    RiskAssessmentHistoryFilters noFilters =
        new RiskAssessmentHistoryFilters(null, null, null, null, null, null);
    AiRiskAssessmentDto lenientResult =
        lenientService
            .findHistory(customerId, noFilters, PageRequest.of(0, 10))
            .getContent()
            .get(0);
    AiRiskAssessmentDto strictResult =
        strictService.findHistory(customerId, noFilters, PageRequest.of(0, 10)).getContent().get(0);

    assertThat(lenientResult.riskLevel()).isEqualTo(RiskLevel.LOW);
    assertThat(strictResult.riskLevel()).isEqualTo(RiskLevel.HIGH);
  }
}
