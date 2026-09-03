import { ActivityType } from './transaction.model';

export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

export type ChronoUnit = 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS';

export interface RangeConstraint {
  minAmount: number;
  minUnit: ChronoUnit;
  maxAmount: number;
  maxUnit: ChronoUnit;
}

export type RangeConstraints = Record<Granularity, RangeConstraint>;

export interface AnalyticsBucket {
  bucketStart: string;
  transactionCount: number;
  amountByCurrency: Record<string, number>;
}

export interface AnalyticsTimeSeries {
  activityType: ActivityType | null;
  granularity: Granularity;
  from: string;
  to: string;
  buckets: AnalyticsBucket[];
}
