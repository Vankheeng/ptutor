import { get } from './httpClient';
import type { StatisticsFilters, StatisticsReport, StatisticsResponse } from '../types/statistics';

export function getStatisticsReport(
  report: StatisticsReport,
  filters: StatisticsFilters,
  accessToken: string,
  signal?: AbortSignal
): Promise<StatisticsResponse> {
  const parameters = new URLSearchParams({
    fromDate: filters.fromDate,
    toDate: filters.toDate,
    timezone: filters.timezone
  });

  if (filters.granularity !== 'AUTO') {
    parameters.set('granularity', filters.granularity);
  }

  return get<StatisticsResponse>(`/api/v1/admin/statistics/${report}?${parameters.toString()}`, {
    signal,
    headers: { Authorization: `Bearer ${accessToken}` }
  });
}
