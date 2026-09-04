package io.github.adarko22.customeractivityanalytics.risk.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adarko22.customeractivityanalytics.AbstractPostgresIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

/**
 * {@code risk_rules} has no natural per-test scoping key (unlike {@code risk_final_assessments},
 * which {@link RiskFinalAssessmentSpecificationsTest} scopes by a random {@code customerId}), and
 * other test classes sharing this suite's static Testcontainers Postgres instance insert {@code
 * risk_rules} rows outside a rolled-back transaction. Every filter call here therefore also scopes
 * on {@code ruleName} containing a per-row random UUID unique to that row, so assertions are immune
 * to rows left behind by other tests and unambiguously distinguish the two rows from each other.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RiskRuleSpecificationsTest extends AbstractPostgresIntegrationTest {

  @Autowired private RiskRuleRepository riskRuleRepository;

  private String cardSuffix;
  private String allSuffix;
  private UUID cardRuleId;
  private UUID allRuleId;

  @BeforeEach
  void setUp() {
    cardSuffix = UUID.randomUUID().toString();
    allSuffix = UUID.randomUUID().toString();
    cardRuleId = UUID.randomUUID();
    allRuleId = UUID.randomUUID();
    riskRuleRepository.save(
        new RiskRule(
            cardRuleId,
            "High-value card transaction " + cardSuffix,
            RuleScope.CARD,
            "Card-present amount exceeds threshold",
            new BigDecimal("30.00")));
    riskRuleRepository.save(
        new RiskRule(
            allRuleId,
            "Generic velocity check " + allSuffix,
            RuleScope.ALL,
            "Too many transactions",
            new BigDecimal("10.00")));
  }

  @Test
  void filtersByAppliesTo() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(RuleScope.CARD, cardSuffix, null, null, null),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(cardRuleId);
  }

  @Test
  void filtersByRuleNameCaseInsensitiveContains() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(null, allSuffix.toUpperCase(), null, null, null),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(allRuleId);
  }

  @Test
  void ruleNameFilterEscapesUnderscoreWildcard() {
    String suffix = UUID.randomUUID().toString();
    UUID literalId = UUID.randomUUID();
    UUID decoyId = UUID.randomUUID();
    riskRuleRepository.save(
        new RiskRule(
            literalId, "under_score " + suffix, RuleScope.ALL, "n/a", new BigDecimal("1.00")));
    // Same length/shape as the literal name above but with the underscore's position replaced
    // by a different character — an unescaped `_` (a single-char SQL LIKE wildcard) would match
    // this row too, since it would match any character at that position.
    riskRuleRepository.save(
        new RiskRule(
            decoyId, "underXscore " + suffix, RuleScope.ALL, "n/a", new BigDecimal("1.00")));

    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(null, "under_score " + suffix, null, null, null),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(literalId);
  }

  @Test
  void filtersByThresholdLogicCaseInsensitiveContains() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(null, cardSuffix, "card-present", null, null),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(cardRuleId);
  }

  @Test
  void filtersByWeightRange() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(
                    null, cardSuffix, null, new BigDecimal("20.00"), new BigDecimal("40.00")),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(cardRuleId);
  }

  @Test
  void combinesAllFiltersWithAnd() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(
                    RuleScope.CARD,
                    cardSuffix,
                    "amount",
                    new BigDecimal("0.00"),
                    new BigDecimal("100.00")),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).containsOnly(cardRuleId);
  }

  @Test
  void noFiltersExcludesNothingOfOurs() {
    List<RiskRule> results =
        riskRuleRepository
            .findAll(
                RiskRuleSpecifications.filter(null, null, null, null, null), PageRequest.of(0, 10))
            .getContent();

    assertThat(results).extracting(RiskRule::getRuleId).contains(cardRuleId, allRuleId);
  }
}
