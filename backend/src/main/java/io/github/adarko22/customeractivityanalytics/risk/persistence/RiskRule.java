package io.github.adarko22.customeractivityanalytics.risk.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "risk_rules")
public class RiskRule {

  @Id
  @Column(name = "rule_id")
  private UUID ruleId;

  @Column(name = "rule_name", nullable = false)
  private String ruleName;

  @Enumerated(EnumType.STRING)
  @Column(name = "applies_to", nullable = false)
  private RuleScope appliesTo;

  @Column(name = "threshold_logic", nullable = false)
  private String thresholdLogic;

  @Column(name = "weight", nullable = false)
  private BigDecimal weight;

  protected RiskRule() {}

  public RiskRule(
      UUID ruleId, String ruleName, RuleScope appliesTo, String thresholdLogic, BigDecimal weight) {
    this.ruleId = ruleId;
    this.ruleName = ruleName;
    this.appliesTo = appliesTo;
    this.thresholdLogic = thresholdLogic;
    this.weight = weight;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  public String getRuleName() {
    return ruleName;
  }

  public RuleScope getAppliesTo() {
    return appliesTo;
  }

  public String getThresholdLogic() {
    return thresholdLogic;
  }

  public BigDecimal getWeight() {
    return weight;
  }
}
