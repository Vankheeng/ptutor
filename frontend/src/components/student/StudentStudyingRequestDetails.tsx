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
        <DetailRow label="Môn học" value={request.subjectName || request.note || 'Môn khác'} />
        <DetailRow label="Lớp" value={request.gradeName} />
        <DetailRow label="Hình thức" value={request.learningMode === 'ONLINE' ? 'Online' : 'Trực tiếp'} />
        <DetailRow
          label="Ngân sách"
          value={`${formatStudyingRequestMoney(request.minPrice)} – ${formatStudyingRequestMoney(request.maxPrice)}`}
        />
        <DetailRow label="Lịch học mong muốn" value={request.preferredSchedule || 'Linh hoạt'} />
        <DetailRow
          label="Địa chỉ"
          value={[request.detailAddress, request.districtName].filter(Boolean).join(', ') || 'Chưa nêu'}
        />
        <DetailRow label="Mục tiêu học" value={request.learningGoals || 'Chưa nêu'} multiline />
        <DetailRow
          label="Lịch rảnh"
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
        <DetailRow label="Cập nhật" value={formatStudyingRequestDate(request.updatedAt)} />
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
