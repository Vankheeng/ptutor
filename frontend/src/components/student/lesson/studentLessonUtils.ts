import type { ContractLesson } from '../../../services/refundApi';

export function isLessonFinished(lesson: ContractLesson) {
  const end = new Date(`${lesson.date}T${lesson.endTime}`);
  return !Number.isNaN(end.getTime()) && end.getTime() <= Date.now();
}
