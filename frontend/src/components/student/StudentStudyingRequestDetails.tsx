import type { StudentStudyingRequestDetail } from '../../services/studentApi';
import { formatStudyingRequestMoney, studyingRequestStatusText } from './studyingRequestUtils';

interface Props {
  request: StudentStudyingRequestDetail;
}

export function StudentStudyingRequestDetails({ request }: Props) {
  return (
    <section className="student-panel student-studying-request-info">
      <h1>{request.title}</h1>
      <dl>
        <div>
          <dt>Trạng thái</dt>
          <dd>
            <span className={`student-status ${request.status.toLowerCase()}`}>
              {studyingRequestStatusText(request.status)}
            </span>
          </dd>
        </div>
        <div>
          <dt>Mô tả</dt>
          <dd>{request.description || 'Chưa có mô tả.'}</dd>
        </div>
        <div>
          <dt>Môn học</dt>
          <dd>{request.subjectName || request.note || 'Môn học tùy chỉnh'}</dd>
        </div>
        <div>
          <dt>Khối lớp</dt>
          <dd>{request.gradeName || 'Chưa xác định'}</dd>
        </div>
        <div>
          <dt>Hình thức</dt>
          <dd>{request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</dd>
        </div>
        <div>
          <dt>Ngân sách</dt>
          <dd>
            {formatStudyingRequestMoney(request.minPrice)} – {formatStudyingRequestMoney(request.maxPrice)}
          </dd>
        </div>
        <div>
          <dt>Khu vực</dt>
          <dd>{[request.detailAddress, request.districtName].filter(Boolean).join(', ') || 'Chưa cung cấp'}</dd>
        </div>
        <div>
          <dt>Mục tiêu học tập</dt>
          <dd>{request.learningGoals || 'Chưa cung cấp'}</dd>
        </div>
        <div>
          <dt>Thời gian có thể học</dt>
          <dd>
            {request.availabilities.length ? (
              <div className="student-studying-request-availability">
                {request.availabilities.map((slot) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${slot.endTime}`}>
                    {weekdayText(slot.dayOfWeek)} {formatRequestTime(slot.startTime)} –{' '}
                    {formatRequestTime(slot.endTime)}
                  </span>
                ))}
              </div>
            ) : (
              'Linh hoạt'
            )}
          </dd>
        </div>
        <div>
          <dt>Thời gian tạo</dt>
          <dd>{formatRequestTimestamp(request.createdAt)}</dd>
        </div>
        <div>
          <dt>Cập nhật lần cuối</dt>
          <dd>{formatRequestTimestamp(request.updatedAt)}</dd>
        </div>
      </dl>
    </section>
  );
}

function formatRequestTime(value: string) {
  return value.slice(0, 5);
}

function formatRequestTimestamp(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/.exec(value);
  if (!match) return value;
  const [, year, month, day, hour, minute, second] = match;
  return `${day}/${month}/${year}, ${hour}:${minute}:${second}`;
}

function weekdayText(dayOfWeek: number) {
  return ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ nhật'][dayOfWeek - 1] ?? 'Chưa xác định';
}
