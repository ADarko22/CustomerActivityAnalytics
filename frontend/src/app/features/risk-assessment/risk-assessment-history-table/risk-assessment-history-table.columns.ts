export interface HistoryColumnDef {
  key: string;
  label: string;
  filterType: 'select' | 'date' | 'score' | 'none';
  selectOptions?: string[];
}

export const HISTORY_COLUMNS: HistoryColumnDef[] = [
  { key: 'triggeredAt', label: 'Triggered At', filterType: 'date' },
  {
    key: 'riskLevel',
    label: 'Risk Level',
    filterType: 'select',
    selectOptions: ['LOW', 'MEDIUM', 'HIGH'],
  },
  { key: 'riskScore', label: 'Score', filterType: 'score' },
  { key: 'findings', label: 'Findings', filterType: 'none' },
  { key: 'recommendations', label: 'Recommendations', filterType: 'none' },
];
