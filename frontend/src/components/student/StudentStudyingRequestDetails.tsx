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
        <DetailRow label="Title" value={request.title} />
        <DetailRow
          label="Status"
          value={
            <span className={`student-status ${request.status.toLowerCase()}`}>{statusLabel(request.status)}</span>
          }
        />
        <DetailRow label="Description" value={request.description || 'No description provided.'} multiline />
        <DetailRow label="Subject" value={request.subjectName || request.note || 'Custom subject'} />
        <DetailRow label="Grade" value={request.gradeName} />
        <DetailRow label="Mode" value={request.learningMode === 'ONLINE' ? 'Online' : 'In person'} />
        <DetailRow
          label="Budget"
          value={`${formatStudyingRequestMoney(request.minPrice)} – ${formatStudyingRequestMoney(request.maxPrice)}`}
        />
        <DetailRow label="Preferred schedule" value={request.preferredSchedule || 'Flexible'} />
        <DetailRow
          label="Address"
          value={[request.detailAddress, request.districtName].filter(Boolean).join(', ') || 'Not specified'}
        />
        <DetailRow label="Learning goals" value={request.learningGoals || 'Not specified'} multiline />
        <DetailRow
          label="Availability"
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
              'Flexible'
            )
          }
          multiline
        />
        <DetailRow label="Created" value={formatStudyingRequestDate(request.createdAt)} />
        <DetailRow label="Updated" value={formatStudyingRequestDate(request.updatedAt)} />
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
