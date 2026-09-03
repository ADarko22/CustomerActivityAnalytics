import { ActivityType } from './transaction.model';

export type Granularity = 'DAY' | 'WEEK' | 'MONTH' | 'YEAR';

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
