import type { TutorStudentFilters } from './components/TutorStudentSearchFilters';

export const initialTutorStudentFilters: TutorStudentFilters = {
  search: '',
  learningMode: 'ALL',
  provinceId: '',
  provinceName: '',
  districtId: '',
  subjectId: '',
  gradeId: '',
  minBudget: '',
  maxBudget: '',
  scheduleSlots: [{ dayOfWeek: '', startTime: '', endTime: '' }]
};
