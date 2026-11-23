import type { RequestStatus } from '../../services/studentApi';
import { studyingRequestStatuses, studyingRequestStatusText } from './studyingRequestUtils';

interface Props {
  keyword: string;
  onKeywordChange: (keyword: string) => void;
  status: 'ALL' | RequestStatus;
  sort: 'ASC' | 'DESC';
  onStatusChange: (status: 'ALL' | RequestStatus) => void;
  onSortChange: (sort: 'ASC' | 'DESC') => void;
}

export function StudentStudyingRequestToolbar({
  keyword,
  onKeywordChange,
  status,
  sort,
  onStatusChange,
  onSortChange
}: Props) {
  return (
    <div className="student-toolbar">
      <label>
        Tìm kiếm
        <input
          type="search"
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          placeholder="Tìm theo tiêu đề hoặc môn học"
          aria-label="Tìm yêu cầu theo tiêu đề hoặc môn học"
        />
      </label>
      <label>
        Trạng thái
        <select value={status} onChange={(event) => onStatusChange(event.target.value as 'ALL' | RequestStatus)}>
          <option value="ALL">Tất cả trạng thái</option>
          {studyingRequestStatuses.slice(1).map((item) => (
            <option key={item} value={item}>
              {studyingRequestStatusText(item)}
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
