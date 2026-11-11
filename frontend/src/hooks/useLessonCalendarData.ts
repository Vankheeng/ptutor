import { useCallback } from 'react';
import { useResource } from './useResource';
import {
  getMyRefundRequests,
  getStudentContractLessons,
  getTutorRefundRequests,
  type ContractLesson,
  type RefundRequest,
  type LessonStatus
} from '../services/refundApi';
import { getContracts } from '../services/studentApi';
import { getTutorContractLessons, type TutorLesson } from '../services/tutorApi';

export type CalendarRole = 'TUTOR' | 'STUDENT';
export type CalendarEvent = {
  id: string;
  contractId: string;
  title: string;
  date: string;
  startTime: string;
  endTime: string;
  status: LessonStatus;
  note: string | null;
  markedTaughtAt: string | null;
  disputed: boolean;
  refundRequestId: string | null;
};

export function useLessonCalendarData(role: CalendarRole, refreshKey: number) {
  const load = useCallback(
    async (signal: AbortSignal) => {
      const contracts = await getContracts(undefined, 0, 100, signal);
      const refundPage =
        role === 'TUTOR' ? await getTutorRefundRequests(0, signal) : await getMyRefundRequests(0, signal);
      const disputeByLesson = new Map<string, string>();
      refundPage.content
        .filter((request: RefundRequest) => request.status === 'PENDING' || request.status === 'IN_REVIEW')
        .forEach((request) =>
          request.disputedLessons.forEach((lesson) => disputeByLesson.set(lesson.lessonId, request.id))
        );

      const lessonsByContract = await Promise.all(
        contracts.content.map(async (contract) => {
          try {
            const lessons: (TutorLesson | ContractLesson)[] =
              role === 'TUTOR'
                ? (await getTutorContractLessons(contract.id, signal)).content
                : await getStudentContractLessons(contract.id, signal);
            return lessons.map((lesson) => ({
              id: lesson.id,
              contractId: contract.id,
              title: lesson.title || contract.subjectName || '',
              date: lesson.date,
              startTime: lesson.startTime,
              endTime: lesson.endTime,
              status: lesson.status,
              note: lesson.note,
              markedTaughtAt: 'markedTaughtAt' in lesson ? lesson.markedTaughtAt : null,
              disputed: disputeByLesson.has(lesson.id),
              refundRequestId: disputeByLesson.get(lesson.id) ?? null
            }));
          } catch {
            return [];
          }
        })
      );

      return { contracts: contracts.content, events: lessonsByContract.flat() };
    },
    [role]
  );

  return useResource(load, refreshKey);
}
