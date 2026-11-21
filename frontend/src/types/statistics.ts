export type StatisticsReport =
  | 'overview'
  | 'students'
  | 'tutors'
  | 'studying-requests'
  | 'teaching-requests'
  | 'matching'
  | 'contracts'
  | 'lessons'
  | 'finance'
  | 'payments'
  | 'installments'
  | 'wallets'
  | 'withdrawals'
  | 'employees';

export type StatisticsGranularity = 'AUTO' | 'DAY' | 'WEEK' | 'MONTH';

export interface StatisticsMetric {
  value: number;
  previousValue: number;
  changePercent: number | null;
}

export interface StatisticsBreakdownItem {
  key: string;
  value: number;
}

export interface StatisticsSeriesItem {
  period: string;
  value: number;
}

export interface StatisticsRange {
  fromDate: string;
  toDate: string;
  timezone: string;
  granularity: Exclude<StatisticsGranularity, 'AUTO'>;
  previousFromDate: string;
  previousToDate: string;
}

export interface StatisticsResponse {
  report: StatisticsReport;
  range: StatisticsRange;
  metrics: Record<string, StatisticsMetric>;
  breakdowns: Record<string, StatisticsBreakdownItem[]>;
  series: Record<string, StatisticsSeriesItem[]>;
  generatedAt: string;
}

export interface StatisticsFilters {
  fromDate: string;
  toDate: string;
  timezone: string;
  granularity: StatisticsGranularity;
}
