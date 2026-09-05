package io.github.adarko22.customeractivityanalytics.risk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.risk.engine.RiskAssessmentProperties;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RiskFinalAssessmentSpecificationsTest extends AbstractPostgresIntegrationTest {

  @Autowired private CustomerRepository customerRepository;
  @Autowired private CardActivityRepository cardActivityRepository;
  @Autowired private RiskFinalAssessmentRepository riskFinalAssessmentRepository;

  private final RiskAssessmentProperties riskProperties =
      new RiskAssessmentProperties(
          5,
          Duration.ofSeconds(45),
          Duration.ofSeconds(50),
          new RiskAssessmentProperties.LevelThresholds(new BigDecimal("30"), new BigDecimal("70")),
          5);

  private UUID customerA;
  private UUID customerB;
  private UUID transaction1;
  private UUID transaction2;
  private Instant t1;
  private Instant t2;
  private Instant t3;

  @BeforeEach
  void setUp() {
    customerA = UUID.randomUUID();
    customerB = UUID.randomUUID();
    customerRepository.save(new Customer(customerA, "Angelo", "Buono"));
    customerRepository.save(new Customer(customerB, "Maria", "Rossi"));

    transaction1 = UUID.randomUUID();
    transaction2 = UUID.randomUUID();
    cardActivityRepository.save(card(transaction1, customerA));
    cardActivityRepository.save(card(transaction2, customerB));

    t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(3, ChronoUnit.DAYS);
    t2 = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(2, ChronoUnit.DAYS);
    t3 = Instant.now().truncatedTo(ChronoUnit.MILLIS).minus(1, ChronoUnit.DAYS);
    riskFinalAssessmentRepository.save(assessment(transaction1, t1, "10.00"));
    riskFinalAssessmentRepository.save(assessment(transaction1, t2, "90.00"));
    riskFinalAssessmentRepository.save(assessment(transaction2, t3, "50.00"));
  }

  @Test
  void scopesResultsToTheGivenCustomerOnly() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA, null, null, null, null, null, null, riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results)
        .extracting(RiskFinalAssessment::getTransactionId)
        .containsOnly(transaction1);
    assertThat(results).hasSize(2);
  }

  @Test
  void filtersByTransactionId() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA, transaction1, null, null, null, null, null, riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(2);
  }

  @Test
  void filtersByRiskLevel() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA, null, RiskLevel.HIGH, null, null, null, null, riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(1);
    assertThat(riskProperties.levelFor(results.get(0).getRiskScore())).isEqualTo(RiskLevel.HIGH);
  }

  @Test
  void filtersByRiskLevelCombinedWithAnotherFilter() {
    // Both transaction1 rows are HIGH-scored under a tightened threshold, but only one belongs
    // to transaction1 — proving riskLevel and transactionId combine with AND, not OR.
    riskFinalAssessmentRepository.save(assessment(transaction2, t3.plusSeconds(1), "95.00"));

    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA,
                    transaction1,
                    RiskLevel.HIGH,
                    null,
                    null,
                    null,
                    null,
                    riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTransactionId()).isEqualTo(transaction1);
    assertThat(results.get(0).getRiskScore()).isEqualByComparingTo("90.00");
  }

  @Test
  void filtersByTriggeredAtRange() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA,
                    null,
                    null,
                    t1.plusSeconds(1),
                    t2.plusSeconds(1),
                    null,
                    null,
                    riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getTriggeredAt()).isEqualTo(t2);
  }

  @Test
  void filtersByScoreRange() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA,
                    null,
                    null,
                    null,
                    null,
                    new BigDecimal("50.00"),
                    new BigDecimal("100.00"),
                    riskProperties),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getRiskScore()).isEqualByComparingTo("90.00");
  }

  private static CardActivity card(UUID transactionId, UUID customerId) {
    return new CardActivity(
        transactionId,
        customerId,
        new BigDecimal("25.00"),
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

  private static RiskFinalAssessment assessment(
      UUID transactionId, Instant triggeredAt, String score) {
    return new RiskFinalAssessment(
        UUID.randomUUID(),
        transactionId,
        triggeredAt,
        new BigDecimal(score),
        "findings",
        "recommendations");
  }
}
