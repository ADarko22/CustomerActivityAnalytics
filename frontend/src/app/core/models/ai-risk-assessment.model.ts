export type AssessmentStage =
  'PROMPT_BUILDING' | 'RULE_RETRIEVAL' | 'HISTORY_RETRIEVAL' | 'MODEL_CALL' | 'COMPLETE' | 'FAILED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface RuleContribution {
  ruleId: string;
  ruleName: string;
  scoreContribution: number;
}

export interface AiRiskAssessment {
  assessmentId: string;
  transactionId: string;
  triggeredAt: string;
  riskLevel: RiskLevel;
  riskScore: number;
  findings: string;
  recommendations: string;
  ruleContributions: RuleContribution[];
}

export interface AiRiskAssessmentEvent {
  stage: AssessmentStage;
  message?: string;
  result?: AiRiskAssessment;
}

export interface AiRiskAssessmentFilter {
  riskLevel?: RiskLevel;
  from?: string;
  to?: string;
  minScore?: number;
  maxScore?: number;
}
