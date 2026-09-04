export type ActivityScope = 'CARD' | 'PAYMENT' | 'CRYPTO' | 'ALL';

export interface RiskRule {
  ruleId: string;
  ruleName: string;
  appliesTo: ActivityScope;
  thresholdLogic: string;
  weight: number;
}

export interface RiskRuleWrite {
  ruleName: string;
  appliesTo: ActivityScope;
  thresholdLogic: string;
  weight: number;
}
