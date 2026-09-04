package io.github.adarko22.customeractivityanalytics.risk;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import io.github.adarko22.customeractivityanalytics.customer.Customer;
import io.github.adarko22.customeractivityanalytics.customer.CustomerRepository;
import io.github.adarko22.customeractivityanalytics.transaction.TransactionStatus;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivity;
import io.github.adarko22.customeractivityanalytics.transaction.card.CardActivityRepository;
import java.math.BigDecimal;
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
    riskFinalAssessmentRepository.save(assessment(transaction1, t1, RiskLevel.LOW, "10.00"));
    riskFinalAssessmentRepository.save(assessment(transaction1, t2, RiskLevel.HIGH, "90.00"));
    riskFinalAssessmentRepository.save(assessment(transaction2, t3, RiskLevel.MEDIUM, "50.00"));
  }

  @Test
  void scopesResultsToTheGivenCustomerOnly() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA, null, null, null, null, null, null),
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
                    customerA, transaction1, null, null, null, null, null),
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
                    customerA, null, RiskLevel.HIGH, null, null, null, null),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).hasSize(1);
    assertThat(results.get(0).getRiskLevel()).isEqualTo(RiskLevel.HIGH);
  }

  @Test
  void filtersByTriggeredAtRange() {
    List<RiskFinalAssessment> results =
        riskFinalAssessmentRepository
            .findAll(
                RiskFinalAssessmentSpecifications.filter(
                    customerA, null, null, t1.plusSeconds(1), t2.plusSeconds(1), null, null),
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
                    new BigDecimal("100.00")),
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
      UUID transactionId, Instant triggeredAt, RiskLevel level, String score) {
    return new RiskFinalAssessment(
        UUID.randomUUID(),
        transactionId,
        triggeredAt,
        level,
        new BigDecimal(score),
        "findings",
        "recommendations");
  }
}
