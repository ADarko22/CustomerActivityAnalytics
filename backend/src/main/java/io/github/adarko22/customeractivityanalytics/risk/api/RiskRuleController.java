package io.github.adarko22.customeractivityanalytics.risk.api;

import io.github.adarko22.customeractivityanalytics.risk.dto.CreateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.RiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.dto.UpdateRiskRuleDto;
import io.github.adarko22.customeractivityanalytics.risk.persistence.RuleScope;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** CRUD over {@code risk_rules}: reads for any operator, writes gated to the {@code ADMIN} role. */
@RestController
public class RiskRuleController {

  private final RiskRuleService riskRuleService;

  public RiskRuleController(RiskRuleService riskRuleService) {
    this.riskRuleService = riskRuleService;
  }

  @GetMapping("/api/v1/risk-rules")
  public Page<RiskRuleDto> findAll(
      @RequestParam(required = false) RuleScope appliesTo,
      @RequestParam(required = false) String ruleName,
      @RequestParam(required = false) String thresholdLogic,
      @RequestParam(required = false) BigDecimal minWeight,
      @RequestParam(required = false) BigDecimal maxWeight,
      @PageableDefault(size = 20, sort = "ruleName") Pageable pageable) {
    return riskRuleService.findAll(
        appliesTo, ruleName, thresholdLogic, minWeight, maxWeight, pageable);
  }

  @PostMapping("/api/v1/risk-rules")
  @ResponseStatus(HttpStatus.CREATED)
  public RiskRuleDto create(@Valid @RequestBody CreateRiskRuleDto dto) {
    return riskRuleService.create(dto);
  }

  @PutMapping("/api/v1/risk-rules/{ruleId}")
  public RiskRuleDto update(@PathVariable UUID ruleId, @Valid @RequestBody UpdateRiskRuleDto dto) {
    return riskRuleService.update(ruleId, dto);
  }

  @DeleteMapping("/api/v1/risk-rules/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID ruleId) {
    riskRuleService.delete(ruleId);
  }
}
