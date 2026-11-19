import type { StudyingRequestSearchResponse } from '../../../services/studyingRequestApi';
import type { TutorTeachingProposal } from '../../../services/tutorApi';
import { Link } from 'react-router-dom';
import { tutorNextStepLabel, tutorScheduleLabel, tutorStatusLabel } from '../../../utils/tutorDisplay';
import { ApplicationSplitCard } from '../../../components/applications/ApplicationSplitCard';

const weekdays = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

interface TutorProposalCardProps {
  proposal: TutorTeachingProposal;
  request?: StudyingRequestSearchResponse;
  contractId?: string;
  cancelling: boolean;
  onCancel: () => void;
  onOpenRequest: () => void;
  onEdit: () => void;
}

export function TutorProposalCard({
  proposal,
  request,
  contractId,
  cancelling,
  onCancel,
  onOpenRequest,
  onEdit
}: TutorProposalCardProps) {
  return (
    <ApplicationSplitCard
      source={{
        eyebrow: 'YÊU CẦU HỌC',
        subject: request?.subjectName || proposal.subjectName || 'Môn học',
        title:
          request?.title ||
          proposal.studyingRequestTitle ||
          `Yêu cầu học môn ${request?.subjectName || proposal.subjectName || 'Môn học'}`,
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
        authorName:
          [request?.studentFirstName ?? proposal.studentFirstName, request?.studentLastName ?? proposal.studentLastName]
            .filter(Boolean)
            .join(' ') || 'Học viên',
        authorProfileId: request?.studentId ?? proposal.studentId,
        authorProfileRole: 'student',
        statusLabel: requestStatusLabel(request, proposal, contractId),
        statusClass: requestStatusClass(request, proposal, contractId),
        createdText:
          request?.createdAt || proposal.studyingRequestCreatedAt
            ? `Đăng ngày ${new Date(request?.createdAt ?? proposal.studyingRequestCreatedAt!).toLocaleDateString('vi-VN')}`
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
        footerText: contractId
          ? 'Đã có hợp đồng'
          : proposal.nextStep
            ? tutorNextStepLabel(proposal.nextStep)
            : `Đã gửi ${new Date(proposal.createdAt).toLocaleDateString('vi-VN')}`,
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
          ) : proposal.status === 'ACCEPTED' && contractId ? (
            <Link className="tutor-button" to={`/tutor/sections/contracts/${contractId}`}>
              Xem hợp đồng
            </Link>
          ) : undefined
      }}
      onCardOpen={onOpenRequest}
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

function studyingRequestStatusLabel(status: string | null | undefined) {
  return (
    {
      DRAFT: 'Bản nháp',
      PENDING_REVIEW: 'Chờ duyệt',
      OPEN: 'Đang mở',
      MATCHED: 'Đã kết nối',
      CLOSED: 'Đã đóng',
      CANCELLED: 'Đã hủy',
      REJECTED: 'Bị từ chối'
    } as Record<string, string>
  )[status ?? 'OPEN'];
}

function requestStatusLabel(
  request: StudyingRequestSearchResponse | undefined,
  proposal: TutorTeachingProposal,
  contractId: string | undefined
) {
  if (proposal.status === 'ACCEPTED' && proposal.nextStep === 'CONTRACT_SIGNING' && !contractId) {
    return 'Chờ học viên tạo hợp đồng';
  }
  return studyingRequestStatusLabel(request?.status ?? proposal.studyingRequestStatus);
}

function requestStatusClass(
  request: StudyingRequestSearchResponse | undefined,
  proposal: TutorTeachingProposal,
  contractId: string | undefined
) {
  if (proposal.status === 'ACCEPTED' && proposal.nextStep === 'CONTRACT_SIGNING' && !contractId) {
    return 'status-request-contract';
  }
  return `status-request-${(request?.status ?? proposal.studyingRequestStatus ?? 'OPEN').toLowerCase()}`;
}
