package io.github.adarko22.customeractivityanalytics.risk.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Aggregate outcome of one assessment run (Feature 7's per-transaction history). */
@Entity
@Table(name = "risk_final_assessments")
public class RiskFinalAssessment {

  @Id
  @Column(name = "assessment_id")
  private UUID assessmentId;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "triggered_at", nullable = false)
  private Instant triggeredAt;

  @Column(name = "risk_score", nullable = false)
  private BigDecimal riskScore;

  @Column(name = "findings", nullable = false)
  private String findings;

  @Column(name = "recommendations", nullable = false)
  private String recommendations;

  protected RiskFinalAssessment() {}

  public RiskFinalAssessment(
      UUID assessmentId,
      UUID transactionId,
      Instant triggeredAt,
      BigDecimal riskScore,
      String findings,
      String recommendations) {
    this.assessmentId = assessmentId;
    this.transactionId = transactionId;
    this.triggeredAt = triggeredAt;
    this.riskScore = riskScore;
    this.findings = findings;
    this.recommendations = recommendations;
  }

  public UUID getAssessmentId() {
    return assessmentId;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public Instant getTriggeredAt() {
    return triggeredAt;
  }

  public BigDecimal getRiskScore() {
    return riskScore;
  }

  public String getFindings() {
    return findings;
  }

  public String getRecommendations() {
    return recommendations;
  }
}
