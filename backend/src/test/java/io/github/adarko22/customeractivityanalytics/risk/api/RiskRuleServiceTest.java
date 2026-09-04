package io.github.adarko22.customeractivityanalytics.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adarko22.customeractivityanalytics.risk.dto.CreateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.RiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.UpdateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRuleRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RiskRuleServiceTest {

  @Mock private RiskRuleRepository riskRuleRepository;

  private RiskRuleService riskRuleService;

  private final UUID ruleId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    riskRuleService = new RiskRuleService(riskRuleRepository);
  }

  @Test
  void findAllDelegatesToSpecificationBasedQuery() {
    Pageable pageable = PageRequest.of(0, 20);
    RiskRule rule = new RiskRule(ruleId, "Rule", RuleScope.ALL, "logic", new BigDecimal("10"));
    when(riskRuleRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(rule)));

    Page<RiskRuleDto> result = riskRuleService.findAll(null, null, null, null, null, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).ruleId()).isEqualTo(ruleId);
  }

  @Test
  void findAllPassesEveryProvidedFilterThrough() {
    Pageable pageable = PageRequest.of(0, 20);
    RiskRule rule = new RiskRule(ruleId, "Rule", RuleScope.CARD, "logic", new BigDecimal("10"));
    when(riskRuleRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(rule)));

    Page<RiskRuleDto> result =
        riskRuleService.findAll(
            RuleScope.CARD, "Rule", "logic", new BigDecimal("5"), new BigDecimal("20"), pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).appliesTo()).isEqualTo(RuleScope.CARD);
  }

  @Test
  void createPersistsAndReturnsNewRule() {
    CreateRiskRuleDto dto =
        new CreateRiskRuleDto("New rule", RuleScope.ALL, "logic", new BigDecimal("15"));
    ArgumentCaptor<RiskRule> captor = ArgumentCaptor.forClass(RiskRule.class);
    when(riskRuleRepository.save(captor.capture()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    RiskRuleDto result = riskRuleService.create(dto);

    assertThat(captor.getValue().getRuleId()).isNotNull();
    assertThat(result.ruleName()).isEqualTo("New rule");
    assertThat(result.weight()).isEqualByComparingTo("15");
  }

  @Test
  void updateThrows404WhenRuleMissing() {
    when(riskRuleRepository.existsById(ruleId)).thenReturn(false);
    UpdateRiskRuleDto dto =
        new UpdateRiskRuleDto("Rule", RuleScope.ALL, "logic", new BigDecimal("10"));

    assertThatThrownBy(() -> riskRuleService.update(ruleId, dto))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Risk rule not found");
    verify(riskRuleRepository, never()).save(any());
  }

  @Test
  void updateSavesReplacementWhenRuleExists() {
    when(riskRuleRepository.existsById(ruleId)).thenReturn(true);
    when(riskRuleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateRiskRuleDto dto =
        new UpdateRiskRuleDto("Updated", RuleScope.PAYMENT, "new logic", new BigDecimal("40"));

    RiskRuleDto result = riskRuleService.update(ruleId, dto);

    assertThat(result.ruleId()).isEqualTo(ruleId);
    assertThat(result.ruleName()).isEqualTo("Updated");
    assertThat(result.appliesTo()).isEqualTo(RuleScope.PAYMENT);
  }

  @Test
  void deleteThrows404WhenRuleMissing() {
    when(riskRuleRepository.existsById(ruleId)).thenReturn(false);

    assertThatThrownBy(() -> riskRuleService.delete(ruleId))
        .isInstanceOf(ResponseStatusException.class);
    verify(riskRuleRepository, never()).deleteById(eq(ruleId));
  }

  @Test
  void deleteRemovesRuleWhenExists() {
    when(riskRuleRepository.existsById(ruleId)).thenReturn(true);

    riskRuleService.delete(ruleId);

    verify(riskRuleRepository).deleteById(ruleId);
  }
}
