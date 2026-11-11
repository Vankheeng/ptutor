import type { StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import type { TutorTeachingProposal } from '../../../services/tutorApi';
import { tutorScheduleLabel, tutorStatusLabel } from '../../../utils/tutorDisplay';
import { ApplicationSplitCard } from '../../../components/applications/ApplicationSplitCard';

const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface TutorProposalCardProps {
  proposal: TutorTeachingProposal;
  request?: StudyingRequestSearchResponse;
  cancelling: boolean;
  onCancel: () => void;
  onOpenRequest: () => void;
  onEdit: () => void;
}

export function TutorProposalCard({
  proposal,
  request,
  cancelling,
  onCancel,
  onOpenRequest,
  onEdit
}: TutorProposalCardProps) {
  return (
    <ApplicationSplitCard
      source={{
        eyebrow: 'YÊU CẦU HỌC',
        subject: request?.subjectName || proposal.subjectName,
        title: request?.title || proposal.studyingRequestTitle || `Yêu cầu học môn ${proposal.subjectName}`,
        grade: request?.gradeName || proposal.gradeName,
        highlights: [
          {
            icon: '◷',
            label: 'Thời gian mong muốn',
            value: request?.preferredSchedule || availability(request) || 'Linh hoạt'
          },
          { icon: '▤', label: 'Lớp học', value: request?.gradeName || proposal.gradeName || 'Chưa xác định' },
          { icon: '₫', label: 'Ngân sách học viên', value: formatBudget(request?.minPrice, request?.maxPrice) },
          ...(request && request.learningMode !== 'ONLINE'
            ? [
                {
                  icon: '⌖',
                  label: 'Địa chỉ',
                  value:
                    [request.detailAddress, request.districtName, request.provinceName].filter(Boolean).join(', ') ||
                    'Chưa cung cấp'
                }
              ]
            : []),
          {
            icon: '▣',
            label: 'Hình thức học',
            value: request ? (request.learningMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp') : '—'
          }
        ],
        authorLabel: 'Đăng bởi',
        authorName: [request?.studentFirstName, request?.studentLastName].filter(Boolean).join(' ') || 'Học viên',
        createdText: request
          ? `Đăng ngày ${new Date(request.createdAt).toLocaleDateString('vi-VN')}`
          : 'Chưa có ngày đăng',
        onOpen: onOpenRequest
      }}
      application={{
        heading: 'Ứng tuyển của tôi',
        statusClass: `status-${proposal.status.toLowerCase()}`,
        statusLabel: tutorStatusLabel(proposal.status),
        price: proposal.proposedPrice,
        summary: [
          {
            label: 'Hình thức dạy',
            value: proposal.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'
          },
          { label: 'Thời gian có thể dạy', value: tutorScheduleLabel(proposal.preferredSchedule) }
        ],
        metadata: [
          { label: 'Thời gian tạo', value: formatDateTime(proposal.createdAt) },
          { label: 'Cập nhật lần cuối', value: formatDateTime(proposal.updatedAt) },
          ...(proposal.status === 'ACCEPTED'
            ? [{ label: 'Thời gian được duyệt', value: formatDateTime(proposal.updatedAt) }]
            : [])
        ],
        notes: [
          ...(proposal.teachingMode === 'OFFLINE' && (proposal.preferredProvinceName || proposal.preferredDistrictName)
            ? [
                {
                  label: 'Địa chỉ dạy học',
                  value: [proposal.preferredDistrictName, proposal.preferredProvinceName].filter(Boolean).join(', ')
                }
              ]
            : []),
          ...(proposal.message?.trim() ? [{ label: 'Lời nhắn', value: proposal.message }] : [])
        ],
        footerText: proposal.nextStep || `Đã gửi ${new Date(proposal.createdAt).toLocaleDateString('vi-VN')}`,
        actions:
          proposal.status === 'PENDING' ? (
            <>
              <button className="tutor-button tutor-proposal-edit" type="button" onClick={onEdit}>
                Sửa đề xuất <span aria-hidden="true">↗</span>
              </button>
              <button className="tutor-button secondary" type="button" disabled={cancelling} onClick={onCancel}>
                {cancelling ? 'Đang hủy…' : 'Hủy đề xuất'}
              </button>
            </>
          ) : undefined
      }}
    />
  );
}

function availability(request?: StudyingRequestSearchResponse) {
  const first = request?.availabilities[0];
  if (!first) return '';
  const more = (request?.availabilities.length ?? 1) - 1;
  return `${weekdays[first.dayOfWeek % 7]} ${first.startTime.slice(0, 5)}–${first.endTime.slice(0, 5)}${more > 0 ? ` +${more} more` : ''}`;
}

function formatBudget(min?: number | null, max?: number | null) {
  const format = (value: number) => new Intl.NumberFormat('vi-VN').format(value);
  if ((min === null || min === undefined) && (max === null || max === undefined)) return 'Có thể thương lượng';
  if (min !== null && min !== undefined && max !== null && max !== undefined)
    return `${format(min)}–${format(max)} VNĐ`;
  return `${format(min ?? max ?? 0)} VNĐ`;
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' });
}
