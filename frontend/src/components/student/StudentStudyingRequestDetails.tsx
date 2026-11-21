import type { ReactNode } from 'react';
import type { StudyingRequest } from '../../services/studentApi';
import {
  formatStudyingRequestDate,
  formatStudyingRequestMoney,
  statusLabel,
  weekdayLabel
} from './studyingRequestUtils';

interface Props {
  request: StudyingRequest;
}

export function StudentStudyingRequestDetails({ request }: Props) {
  return (
    <section className="student-panel">
      <div className="student-detail-table">
        <DetailRow label="Tiêu đề" value={request.title} />
        <DetailRow
          label="Trạng thái"
          value={
            <span className={`student-status ${request.status.toLowerCase()}`}>{statusLabel(request.status)}</span>
          }
        />
        <DetailRow label="Mô tả" value={request.description || 'Chưa có mô tả.'} multiline />
        <DetailRow label="Môn học" value={request.subjectName || request.note || 'Môn học tùy chỉnh'} />
        <DetailRow label="Khối lớp" value={request.gradeName} />
        <DetailRow label="Hình thức" value={request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
        <DetailRow
          label="Budget"
          value={`${formatStudyingRequestMoney(request.minPrice)} – ${formatStudyingRequestMoney(request.maxPrice)}`}
        />
        <DetailRow label="Lịch mong muốn" value={request.preferredSchedule || 'Linh hoạt'} />
        <DetailRow
          label="Địa chỉ"
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
                    {weekdayLabel(slot.dayOfWeek)}: {slot.startTime} – {slot.endTime}
                  </span>
                ))}
              </div>
            ) : (
              'Linh hoạt'
            )
          }
          multiline
        />
        <DetailRow label="Ngày tạo" value={formatStudyingRequestDate(request.createdAt)} />
        <DetailRow label="Cập nhật lần cuối" value={formatStudyingRequestDate(request.updatedAt)} />
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
