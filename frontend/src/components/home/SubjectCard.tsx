import type { Subject } from '../../types/api';
import { Icon } from '../ui/Icon';

const colors = ['violet', 'blue', 'orange', 'pink', 'green', 'indigo'];
const symbols = ['∑', 'A', '⚛', '⌬', '✎', '</>'];
interface SubjectCardProps {
  subject: Subject;
  index: number;
}

export function SubjectCard({ subject, index }: SubjectCardProps) {
  return (
    <a href="#tutors" className="subject-card">
      <span className={`subject-icon ${colors[index % colors.length]}`}>{symbols[index % symbols.length]}</span>
      <span>
        <strong>{subject.name}</strong>
        <small>{subject.description || 'Khám phá gia sư và lớp học phù hợp'}</small>
      </span>
      <span className="subject-arrow">
        <Icon name="arrow" size={18} />
      </span>
    </a>
  );
}
