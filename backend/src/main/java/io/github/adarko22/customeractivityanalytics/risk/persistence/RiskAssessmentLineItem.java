package io.github.adarko22.customeractivityanalytics.risk.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One matched-and-retained rule within a {@link RiskFinalAssessment} run. */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessmentLineItem {

  @EmbeddedId private RiskAssessmentLineItemId id;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "triggered_at", nullable = false)
  private Instant triggeredAt;

  @Column(name = "score_contribution", nullable = false)
  private BigDecimal scoreContribution;

  protected RiskAssessmentLineItem() {}

  public RiskAssessmentLineItem(
      UUID assessmentId,
      UUID ruleId,
      UUID transactionId,
      Instant triggeredAt,
      BigDecimal scoreContribution) {
    this.id = new RiskAssessmentLineItemId(assessmentId, ruleId);
    this.transactionId = transactionId;
    this.triggeredAt = triggeredAt;
    this.scoreContribution = scoreContribution;
  }

  public RiskAssessmentLineItemId getId() {
    return id;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public Instant getTriggeredAt() {
    return triggeredAt;
  }

  public BigDecimal getScoreContribution() {
    return scoreContribution;
  }
}
