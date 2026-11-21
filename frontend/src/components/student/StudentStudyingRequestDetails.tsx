import type { ReactNode } from 'react';
import type { StudentStudyingRequestDetail } from '../../services/studentApi';
import { formatStudyingRequestMoney, studyingRequestStatusText } from './studyingRequestUtils';

interface Props {
  request: StudentStudyingRequestDetail;
}

export function StudentStudyingRequestDetails({ request }: Props) {
  return (
    <section className="student-panel student-studying-request-info">
      <div className="student-detail-table">
        <DetailRow label="Tiêu đề" value={request.title} />
        <DetailRow
          label="Trạng thái"
          value={
            <span className={`student-status ${request.status.toLowerCase()}`}>
              {studyingRequestStatusText(request.status)}
            </span>
          }
        />
        <DetailRow label="Mô tả" value={request.description || 'Chưa có mô tả.'} multiline />
        <DetailRow label="Môn học" value={request.subjectName || request.note || 'Môn học tùy chỉnh'} />
        <DetailRow label="Khối lớp" value={request.gradeName || 'Chưa xác định'} />
        <DetailRow label="Hình thức học" value={request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
        <DetailRow
          label="Ngân sách"
          value={`${formatStudyingRequestMoney(request.minPrice)} – ${formatStudyingRequestMoney(request.maxPrice)}`}
        />
        <DetailRow
          label="Khu vực học"
          value={[request.detailAddress, request.districtName].filter(Boolean).join(', ') || 'Chưa cung cấp'}
        />
        <DetailRow label="Mục tiêu học tập" value={request.learningGoals || 'Chưa cung cấp'} multiline />
        <DetailRow
          label="Thời gian có thể học"
          value={
            request.availabilities.length ? (
              <div className="student-detail-availability">
                {request.availabilities.map((slot) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}-${slot.endTime}`}>
                    {weekdayText(slot.dayOfWeek)} {formatRequestTime(slot.startTime)} –{' '}
                    {formatRequestTime(slot.endTime)}
                  </span>
                ))}
              </div>
            ) : (
              'Linh hoạt'
            )
          }
          multiline
        />
        <DetailRow label="Ngày tạo" value={formatRequestTimestamp(request.createdAt)} />
        <DetailRow label="Cập nhật lần cuối" value={formatRequestTimestamp(request.updatedAt)} />
      </div>
    </section>
  );
}

function DetailRow({ label, value, multiline = false }: { label: string; value: ReactNode; multiline?: boolean }) {
  return (
    <div className={`student-detail-row${multiline ? ' multiline' : ''}`}>
      <span>{label}</span>
      <div className="student-detail-value">{value}</div>
    </div>
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
