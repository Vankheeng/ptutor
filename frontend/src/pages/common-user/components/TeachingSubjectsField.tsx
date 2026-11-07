import type { Subject } from '../../../types/api';

interface Props {
  subjects: Subject[];
  values: string[];
  onChange: (values: string[]) => void;
}

export function TeachingSubjectsField({ subjects, values, onChange }: Props) {
  const activeSubjects = subjects.filter((subject) => subject.status === 'ACTIVE');
  const customSelected = values.includes('OTHER');
  const toggle = (id: string) =>
    onChange(values.includes(id) ? values.filter((value) => value !== id) : [...values, id]);

  return (
    <fieldset className="student-field-full teaching-choice-field">
      <legend>Môn có thể dạy (chọn một hoặc nhiều)</legend>
      <div className="teaching-choice-grid">
        {activeSubjects.map((subject) => (
          <label key={subject.id}>
            <input type="checkbox" checked={values.includes(subject.id)} onChange={() => toggle(subject.id)} />
            <span>{subject.name}</span>
          </label>
        ))}
        <label>
          <input type="checkbox" checked={customSelected} onChange={() => toggle('OTHER')} />
          <span>Môn học khác</span>
        </label>
      </div>
      {customSelected && <p className="teaching-choice-hint">Tên môn học khác sẽ được nhập sau khi chọn.</p>}
    </fieldset>
  );
}
