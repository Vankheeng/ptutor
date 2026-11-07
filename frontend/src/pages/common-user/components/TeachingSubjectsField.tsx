import type { Subject } from '../../../types/api';
import { MultiSelectDropdown } from './MultiSelectDropdown';

interface Props {
  subjects: Subject[];
  values: string[];
  onChange: (values: string[]) => void;
}

export function TeachingSubjectsField({ subjects, values, onChange }: Props) {
  const activeSubjects = subjects.filter((subject) => subject.status === 'ACTIVE');
  const customSelected = values.includes('OTHER');

  return (
    <div className="student-field-full">
      <MultiSelectDropdown
        label="Môn có thể dạy"
        placeholder="Chọn một hoặc nhiều môn học"
        options={[
          ...activeSubjects.map((subject) => ({ value: subject.id, label: subject.name })),
          { value: 'OTHER', label: 'Môn học khác' }
        ]}
        values={values}
        onChange={onChange}
      />
      {customSelected && <p className="teaching-choice-hint">Tên môn học khác sẽ được nhập sau khi chọn.</p>}
    </div>
  );
}
