package io.github.adarko22.customeractivityanalytics.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.transaction.ActivityType;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskRuleRetrievalServiceTest {

  @Mock private RiskRuleRepository riskRuleRepository;

  @Test
  void findsRulesScopedToTheActivityTypePlusAll() {
    RiskRule cardRule =
        new RiskRule(
            UUID.randomUUID(), "Card rule", RuleScope.CARD, "condition", new BigDecimal("10.00"));
    when(riskRuleRepository.findByAppliesToIn(eq(EnumSet.of(RuleScope.CARD, RuleScope.ALL))))
        .thenReturn(List.of(cardRule));

    RiskRuleRetrievalService service = new RiskRuleRetrievalService(riskRuleRepository);
    List<RiskRule> result = service.findApplicable(ActivityType.CARD);

    assertThat(result).containsExactly(cardRule);
  }

  @Test
  void mapsEachActivityTypeToItsOwnScope() {
    RiskRuleRetrievalService service = new RiskRuleRetrievalService(riskRuleRepository);

    service.findApplicable(ActivityType.PAYMENT);

    verify(riskRuleRepository).findByAppliesToIn(EnumSet.of(RuleScope.PAYMENT, RuleScope.ALL));
  }
}
