import { useCallback, useMemo, useState, type FormEvent } from 'react';
import { getGrades } from '../services/gradeApi';
import { getReviews } from '../services/reviewApi';
import { getSubjects } from '../services/subjectApi';
import { getTeachingRequests, type TeachingRequestFilters } from '../services/teachingRequestApi';
import { useResource } from './useResource';

const SUBJECTS_PER_PAGE = 6;

export function useHomeLanding() {
  const [searchMessage, setSearchMessage] = useState('');
  const [subjectPage, setSubjectPage] = useState(0);
  const [searchFilters, setSearchFilters] = useState<TeachingRequestFilters>({});
  const subjects = useResource(useCallback((signal: AbortSignal) => getSubjects('popular', signal), []));
  const grades = useResource(useCallback((signal: AbortSignal) => getGrades(signal), []));
  const posts = useResource(
    useCallback((signal: AbortSignal) => getTeachingRequests(3, searchFilters, signal), [searchFilters])
  );
  const reviews = useResource(useCallback((signal: AbortSignal) => getReviews(3, signal), []));

  const subjectPageCount = Math.ceil((subjects.data?.length ?? 0) / SUBJECTS_PER_PAGE);
  const currentSubjectPage = Math.min(subjectPage, Math.max(subjectPageCount - 1, 0));
  const visibleSubjects = useMemo(
    () => subjects.data?.slice(currentSubjectPage * SUBJECTS_PER_PAGE, (currentSubjectPage + 1) * SUBJECTS_PER_PAGE),
    [currentSubjectPage, subjects.data]
  );

  const handleSearch = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const formData = new FormData(event.currentTarget);
      const subjectId = formData.get('subjectId');
      const gradeId = formData.get('gradeId');
      const selectedSubjectId = typeof subjectId === 'string' && subjectId ? subjectId : undefined;
      const selectedGradeId = typeof gradeId === 'string' && gradeId ? gradeId : undefined;
      const subjectName = subjects.data?.find((subject) => subject.id === selectedSubjectId)?.name;
      const gradeName = grades.data?.find((grade) => grade.id === selectedGradeId)?.name;

      setSearchFilters({ subjectId: selectedSubjectId, gradeId: selectedGradeId });
      setSearchMessage(
        subjectName || gradeName
          ? `Đang tìm lớp ${[subjectName, gradeName].filter(Boolean).join(' · ')}.`
          : 'Đang hiển thị các lớp học mới nhất.'
      );
    },
    [grades.data, subjects.data]
  );

  return {
    subjects,
    grades,
    posts,
    reviews,
    visibleSubjects,
    currentSubjectPage,
    subjectPageCount,
    setSubjectPage,
    searchMessage,
    handleSearch,
    previousSubjectPage: () => setSubjectPage((page) => Math.max(page - 1, 0)),
    nextSubjectPage: () => setSubjectPage((page) => Math.min(page + 1, subjectPageCount - 1))
  };
}
