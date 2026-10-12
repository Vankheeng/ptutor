import { useCallback } from 'react';
import { useResource } from './useResource';
import {
  getContracts,
  getMyStudyingRequests,
  getStudentApplications,
  getUnreadCount,
  getWalletBalance,
  type Contract,
  type PageResponse,
  type StudentApplication,
  type StudyingRequest,
  type WalletBalance
} from '../services/studentApi';

export interface StudentDashboardData {
  contracts: PageResponse<Contract> | null;
  studyingRequests: PageResponse<StudyingRequest> | null;
  applications: PageResponse<StudentApplication> | null;
  unreadCount: number | null;
  wallet: WalletBalance | null;
  errors: string[];
}

export function useStudentDashboard() {
  const load = useCallback(async (signal: AbortSignal): Promise<StudentDashboardData> => {
    const results = await Promise.allSettled([
      getContracts(undefined, 0, 100, signal),
      getMyStudyingRequests(undefined, 0, 100, signal),
      getStudentApplications(undefined, 0, 100, signal),
      getUnreadCount(signal),
      getWalletBalance(signal)
    ]);
    const value = <T>(index: number) => (results[index].status === 'fulfilled' ? (results[index].value as T) : null);
    return {
      contracts: value<PageResponse<Contract>>(0),
      studyingRequests: value<PageResponse<StudyingRequest>>(1),
      applications: value<PageResponse<StudentApplication>>(2),
      unreadCount: value<{ unreadCount: number }>(3)?.unreadCount ?? null,
      wallet: value<WalletBalance>(4),
      errors: results
        .filter((result) => result.status === 'rejected')
        .map(() => 'Some dashboard data could not be loaded.')
    };
  }, []);
  return useResource<StudentDashboardData>(load);
}
