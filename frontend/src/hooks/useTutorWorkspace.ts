import { useCallback } from 'react';
import { useResource } from './useResource';
import {
  filterTutorCertificates,
  getTutorContracts,
  getTutorContractLessons,
  getTutorProfile,
  getTutorProposals,
  type TutorCertificate,
  type TutorContract,
  type TutorLesson,
  type TutorProfile,
  type TutorTeachingProposal
} from '../services/tutorApi';

export function useTutorProfile(revision = 0) {
  const load = useCallback((signal: AbortSignal) => getTutorProfile(signal), []);
  return useResource<TutorProfile>(load, revision);
}

export function useTutorCertificates(status?: TutorCertificate['status'], revision = 0) {
  const load = useCallback((signal: AbortSignal) => filterTutorCertificates(status, signal), [status]);
  return useResource<TutorCertificate[]>(load, revision);
}

export function useTutorProposals(status?: TutorTeachingProposal['status'], revision = 0) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      const page = await getTutorProposals(status, signal);
      return page.content;
    },
    [status]
  );
  return useResource<TutorTeachingProposal[]>(load, revision);
}

export interface TutorDashboardData {
  contracts: TutorContract[];
  upcomingLessons: TutorLesson[];
}

export function useTutorDashboard() {
  const load = useCallback(async (signal: AbortSignal): Promise<TutorDashboardData> => {
    const page = await getTutorContracts(signal);
    const contracts = page.content;
    const active = contracts.filter((contract) => contract.status === 'ACTIVE');
    const lessonsByContract = await Promise.all(active.map((contract) => getTutorContractLessons(contract.id, signal)));
    const start = localDateString(new Date());
    const endDate = new Date();
    endDate.setDate(endDate.getDate() + 7);
    const end = localDateString(endDate);
    const upcomingLessons = lessonsByContract
      .flatMap((lessonPage) => lessonPage.content)
      .filter((lesson) => lesson.status === 'SCHEDULED' && lesson.date >= start && lesson.date <= end)
      .sort((left, right) => `${left.date}T${left.startTime}`.localeCompare(`${right.date}T${right.startTime}`));
    return { contracts, upcomingLessons };
  }, []);
  return useResource<TutorDashboardData>(load);
}

function localDateString(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
