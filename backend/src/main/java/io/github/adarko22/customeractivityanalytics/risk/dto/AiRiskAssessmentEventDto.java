package io.github.adarko22.customeractivityanalytics.risk.dto;

/**
 * SSE payload: a progress token for every stage, {@code result} populated only on {@code COMPLETE}.
 */
public record AiRiskAssessmentEventDto(
    AssessmentStage stage, String message, AiRiskAssessmentDto result) {

  public static AiRiskAssessmentEventDto progress(AssessmentStage stage) {
    return new AiRiskAssessmentEventDto(stage, null, null);
  }

  public static AiRiskAssessmentEventDto complete(AiRiskAssessmentDto result) {
    return new AiRiskAssessmentEventDto(AssessmentStage.COMPLETE, null, result);
  }

  public static AiRiskAssessmentEventDto failed(String message) {
    return new AiRiskAssessmentEventDto(AssessmentStage.FAILED, message, null);
  }
}
