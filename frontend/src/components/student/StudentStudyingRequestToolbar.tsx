import type { RequestStatus } from '../../services/studentApi';
import { studyingRequestStatuses, statusLabel } from './studyingRequestUtils';

interface Props {
  status: 'ALL' | RequestStatus;
  sort: 'ASC' | 'DESC';
  onStatusChange: (status: 'ALL' | RequestStatus) => void;
  onSortChange: (sort: 'ASC' | 'DESC') => void;
}

export function StudentStudyingRequestToolbar({ status, sort, onStatusChange, onSortChange }: Props) {
  return (
    <div className="student-toolbar">
      <label>
        Trạng thái
        <select value={status} onChange={(event) => onStatusChange(event.target.value as 'ALL' | RequestStatus)}>
          <option value="ALL">Tất cả trạng thái</option>
          {studyingRequestStatuses.slice(1).map((item) => (
            <option key={item} value={item}>
              {statusLabel(item)}
            </option>
          ))}
        </select>
      </label>
      <label>
        Sắp xếp
        <select value={sort} onChange={(event) => onSortChange(event.target.value as 'ASC' | 'DESC')}>
          <option value="DESC">Mới nhất trước</option>
          <option value="ASC">Cũ nhất trước</option>
        </select>
      </label>
    </div>
  );
}
