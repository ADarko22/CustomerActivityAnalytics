package io.github.adarko22.customeractivityanalytics.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RiskAssessmentLineItemId implements Serializable {

  @Column(name = "assessment_id")
  private UUID assessmentId;

  @Column(name = "rule_id")
  private UUID ruleId;

  protected RiskAssessmentLineItemId() {}

  public RiskAssessmentLineItemId(UUID assessmentId, UUID ruleId) {
    this.assessmentId = assessmentId;
    this.ruleId = ruleId;
  }

  public UUID getAssessmentId() {
    return assessmentId;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RiskAssessmentLineItemId that)) {
      return false;
    }
    return Objects.equals(assessmentId, that.assessmentId) && Objects.equals(ruleId, that.ruleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assessmentId, ruleId);
  }
}
