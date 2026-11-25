import { useEffect, useState } from 'react';
import { getGrades } from '../services/gradeApi';
import { getSubjects } from '../services/subjectApi';

export function useStudyCatalogOptions() {
  const [subjects, setSubjects] = useState<{ id: string; name: string }[]>([]);
  const [grades, setGrades] = useState<{ id: string; name: string }[]>([]);

  useEffect(() => {
    let active = true;
    Promise.all([getSubjects(), getGrades()])
      .then(([subjectItems, gradeItems]) => {
        if (active) {
          setSubjects(subjectItems);
          setGrades(gradeItems);
        }
      })
      .catch(() => {
        if (active) {
          setSubjects([]);
          setGrades([]);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  return { subjects, grades };
}
