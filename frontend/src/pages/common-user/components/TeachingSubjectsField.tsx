import type { Subject } from '../../../types/api';

interface Props {
  subjects: Subject[];
  values: string[];
  onChange: (values: string[]) => void;
}

export function TeachingSubjectsField({ subjects, values, onChange }: Props) {
  const activeSubjects = subjects.filter((subject) => subject.status === 'ACTIVE');
  const selectedSubject = values[0] ?? '';

  return (
    <label className={selectedSubject === 'OTHER' ? undefined : 'student-field-full'}>
      Môn có thể dạy
      <select value={selectedSubject} onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}>
        <option value="">Chọn một môn học</option>
        {activeSubjects.map((subject) => (
          <option key={subject.id} value={subject.id}>
            {subject.name}
          </option>
        ))}
        <option value="OTHER">Môn học khác</option>
      </select>
    </label>
  );
}
