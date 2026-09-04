export interface RiskRuleColumnDef {
  key: string;
  label: string;
  filterType: 'text' | 'select' | 'weight' | 'none';
  selectOptions?: string[];
}

export const RISK_RULE_COLUMNS: RiskRuleColumnDef[] = [
  { key: 'ruleName', label: 'Rule Name', filterType: 'text' },
  {
    key: 'appliesTo',
    label: 'Applies To',
    filterType: 'select',
    selectOptions: ['CARD', 'PAYMENT', 'CRYPTO', 'ALL'],
  },
  { key: 'thresholdLogic', label: 'Threshold Logic', filterType: 'text' },
  { key: 'weight', label: 'Weight', filterType: 'weight' },
  { key: 'actions', label: '', filterType: 'none' },
];
