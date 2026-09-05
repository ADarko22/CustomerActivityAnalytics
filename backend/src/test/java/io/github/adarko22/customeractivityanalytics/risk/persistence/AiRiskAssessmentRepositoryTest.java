package io.github.adarko22.customeractivityanalytics.risk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionCoreFields;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityDetails;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiRiskAssessmentRepositoryTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CardActivityRepository cardActivityRepository;
  @Autowired private RiskRuleRepository riskRuleRepository;
  @Autowired private RiskFinalAssessmentRepository riskFinalAssessmentRepository;
  @Autowired private RiskAssessmentLineItemRepository riskAssessmentLineItemRepository;

  private UUID transactionId;
  private RiskRule ruleA;
  private RiskRule ruleB;

  @BeforeEach
  void setUp() {
    UUID customerId = UUID.randomUUID();
    customerRepository.save(new Customer(customerId, "Angelo", "Buono"));
    transactionId = UUID.randomUUID();
    cardActivityRepository.save(
        new CardActivity(
            new TransactionCoreFields(
                transactionId,
                customerId,
                new BigDecimal("25.00"),
                "EUR",
                TransactionStatus.COMPLETED,
                Instant.now()),
            new CardActivityDetails("****1234", "DEBIT", "Amazon", "5732", true, "AUTH1", null)));
    ruleA =
        riskRuleRepository.save(
            new RiskRule(
                UUID.randomUUID(),
                "High-value transaction",
                RuleScope.ALL,
                "amount > 5000",
                new BigDecimal("25.00")));
    ruleB =
        riskRuleRepository.save(
            new RiskRule(
                UUID.randomUUID(),
                "Card-not-present",
                RuleScope.CARD,
                "cardPresent = false",
                new BigDecimal("15.00")));
  }

  @Test
  void persistsAndReloadsTheTwoTableModelByCompositeKey() {
    Instant triggeredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    RiskFinalAssessment finalAssessment =
        riskFinalAssessmentRepository.save(
            new RiskFinalAssessment(
                UUID.randomUUID(),
                transactionId,
                triggeredAt,
                new BigDecimal("32.50"),
                "findings",
                "recommendations"));
    UUID assessmentId = finalAssessment.getAssessmentId();

    riskAssessmentLineItemRepository.save(
        new RiskAssessmentLineItem(
            assessmentId, ruleA.getRuleId(), transactionId, triggeredAt, new BigDecimal("20.00")));
    riskAssessmentLineItemRepository.save(
        new RiskAssessmentLineItem(
            assessmentId, ruleB.getRuleId(), transactionId, triggeredAt, new BigDecimal("12.50")));

    Optional<RiskAssessmentLineItem> reloaded =
        riskAssessmentLineItemRepository.findById(
            new RiskAssessmentLineItemId(assessmentId, ruleA.getRuleId()));

    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getScoreContribution()).isEqualByComparingTo("20.00");
    assertThat(riskFinalAssessmentRepository.findById(assessmentId)).isPresent();
  }

  @Test
  void joinsLineItemsToRuleNamesForAPageOfAssessments() {
    Instant triggeredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    RiskFinalAssessment assessment1 =
        riskFinalAssessmentRepository.save(
            new RiskFinalAssessment(
                UUID.randomUUID(),
                transactionId,
                triggeredAt,
                new BigDecimal("20.00"),
                "findings",
                "recommendations"));
    riskAssessmentLineItemRepository.save(
        new RiskAssessmentLineItem(
            assessment1.getAssessmentId(),
            ruleA.getRuleId(),
            transactionId,
            triggeredAt,
            new BigDecimal("20.00")));

    List<RuleContributionRow> rows =
        riskAssessmentLineItemRepository.findByAssessmentIdInWithRuleName(
            List.of(assessment1.getAssessmentId()));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).assessmentId()).isEqualTo(assessment1.getAssessmentId());
    assertThat(rows.get(0).ruleName()).isEqualTo("High-value transaction");
    assertThat(rows.get(0).scoreContribution()).isEqualByComparingTo("20.00");
  }
}
