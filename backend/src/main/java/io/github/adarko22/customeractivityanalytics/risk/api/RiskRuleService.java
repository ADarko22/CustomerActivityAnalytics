package io.github.adarko22.customeractivityanalytics.risk.api;

import io.github.adarko22.customeractivityanalytics.risk.dto.CreateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.RiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.UpdateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRule;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRuleRepository;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RiskRuleSpecifications;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Admin CRUD over {@code risk_rules} (docs/development/PHASE_5_PLAN.md). */
@Service
public class RiskRuleService {

  private static final Logger log = LoggerFactory.getLogger(RiskRuleService.class);

  private final RiskRuleRepository riskRuleRepository;

  public RiskRuleService(RiskRuleRepository riskRuleRepository) {
    this.riskRuleRepository = riskRuleRepository;
  }

  public Page<RiskRuleDto> findAll(
      RuleScope appliesTo,
      String ruleName,
      String thresholdLogic,
      BigDecimal minWeight,
      BigDecimal maxWeight,
      Pageable pageable) {
    Specification<RiskRule> spec =
        RiskRuleSpecifications.filter(appliesTo, ruleName, thresholdLogic, minWeight, maxWeight);
    return riskRuleRepository.findAll(spec, pageable).map(this::toDto);
  }

  @Transactional
  public RiskRuleDto create(CreateRiskRuleDto dto) {
    RiskRule saved =
        riskRuleRepository.save(
            new RiskRule(
                UUID.randomUUID(),
                dto.ruleName(),
                dto.appliesTo(),
                dto.thresholdLogic(),
                dto.weight()));
    log.info("Created risk rule: ruleId={}, appliesTo={}", saved.getRuleId(), saved.getAppliesTo());
    return toDto(saved);
  }

  @Transactional
  public RiskRuleDto update(UUID ruleId, UpdateRiskRuleDto dto) {
    requireExists(ruleId);
    RiskRule saved =
        riskRuleRepository.save(
            new RiskRule(
                ruleId, dto.ruleName(), dto.appliesTo(), dto.thresholdLogic(), dto.weight()));
    log.info("Updated risk rule: ruleId={}", ruleId);
    return toDto(saved);
  }

  @Transactional
  public void delete(UUID ruleId) {
    requireExists(ruleId);
    riskRuleRepository.deleteById(ruleId);
    log.info("Deleted risk rule: ruleId={}", ruleId);
  }

  private void requireExists(UUID ruleId) {
    if (!riskRuleRepository.existsById(ruleId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Risk rule not found: " + ruleId);
    }
  }

  private RiskRuleDto toDto(RiskRule rule) {
    return new RiskRuleDto(
        rule.getRuleId(),
        rule.getRuleName(),
        rule.getAppliesTo(),
        rule.getThresholdLogic(),
        rule.getWeight());
  }
}
