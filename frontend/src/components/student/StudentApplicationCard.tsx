import { useNavigate } from 'react-router-dom';
import { ApplicationSplitCard } from '../applications/ApplicationSplitCard';
import type { StudentApplication } from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { applicationStatusLabel, formatDate, formatMoney, learningModeLabel } from './studentApplicationUtils';

interface Props {
  application: StudentApplication;
  request?: TeachingRequest;
  cancelling: boolean;
  onEdit: () => void;
  onCancel: () => void;
}

export function StudentApplicationCard({ application, request, cancelling, onEdit, onCancel }: Props) {
  const navigate = useNavigate();
  const subject =
    request?.subjectName ||
    request?.customSubjectName ||
    application.subjectName ||
    application.customSubjectName ||
    'Môn học tùy chỉnh';
  const title = request?.title || application.teachingRequestTitle || subject;
  const grade = application.gradeName || request?.grades.map((item) => item.name).join(', ') || 'Chưa xác định';
  const tutorName =
    request?.tutorName || [application.tutorFirstName, application.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const learningMode = request?.teachingMode || application.learningMode;
  const preferredSchedule = request?.preferredSchedule || application.preferredSchedule || 'Linh hoạt';
  const requestAddress = [request?.detailAddress, ...(request?.districts.map((item) => item.name) ?? [])]
    .filter(Boolean)
    .join(', ');

  return (
    <ApplicationSplitCard
      source={{
        eyebrow: 'YÊU CẦU DẠY',
        subject,
        title,
        grade,
        highlights: [
          { icon: '◷', label: 'Thời gian mong muốn', value: preferredSchedule },
          { icon: '▤', label: 'Lớp học', value: grade },
          {
            icon: '₫',
            label: 'Mức giá dự kiến',
            value: formatMoney(request?.expectedPrice ?? application.proposedPrice)
          },
          ...(learningMode === 'OFFLINE'
            ? [{ icon: '⌖', label: 'Địa chỉ', value: requestAddress || 'Chưa cung cấp' }]
            : []),
          { icon: '▣', label: 'Hình thức học', value: learningModeLabel(learningMode) }
        ],
        authorLabel: 'Đăng bởi',
        authorName: tutorName,
        createdText: request ? `Đăng ngày ${formatDate(request.createdAt)}` : 'Đã gửi yêu cầu'
      }}
      application={{
        heading: 'Ứng tuyển của tôi',
        statusClass: `status-${application.status.toLowerCase()}`,
        statusLabel: applicationStatusLabel(application.status),
        price: application.proposedPrice,
        summary: [
          { label: 'Hình thức học', value: learningModeLabel(application.learningMode) },
          { label: 'Lịch mong muốn', value: application.preferredSchedule || 'Linh hoạt' }
        ],
        notes: application.message?.trim() ? [{ label: 'Lời nhắn', value: application.message }] : undefined,
        footerText: application.nextStep || `Đã gửi ${formatDate(application.createdAt)}`,
        actions: (
          <>
            {application.status === 'PENDING' && (
              <>
                <button className="tutor-button tutor-proposal-edit" type="button" onClick={onEdit}>
                  Sửa đề xuất <span aria-hidden="true">↗</span>
                </button>
                <button className="tutor-button secondary" type="button" disabled={cancelling} onClick={onCancel}>
                  {cancelling ? 'Đang hủy…' : 'Hủy ứng tuyển'}
                </button>
              </>
            )}
          </>
        )
      }}
      onCardOpen={() => navigate(`/student/applications/${application.id}`)}
      className="student-application-card"
    />
  );
}
