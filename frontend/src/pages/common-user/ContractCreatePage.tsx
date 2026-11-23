import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { useAuth } from '../../auth/useAuth';
import { ApiError } from '../../services/httpClient';
import {
  createStudentContract,
  checkStudentContractSchedule,
  getMyStudyingRequest,
  getTutorOffer,
  type ContractTerms,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import {
  createTeachingRequestContract,
  checkTeachingRequestContractSchedule,
  getMyTeachingRequest,
  getTeachingRequestApplications,
  type StudentTeachingApplication
} from '../../services/myTeachingRequestApi';
import type { TeachingRequest } from '../../types/api';
import { useResource } from '../../hooks/useResource';
import { Icon } from '../../components/ui/Icon';
import { ContractParticipantCard } from './components/ContractParticipantCard';
import { ContractTermsForm } from './components/ContractTermsForm';
import { notifyGlobalToast } from '../../components/ui/globalToast';

type CreateRole = 'student' | 'tutor';
type CreateRequest = StudyingRequest | TeachingRequest;
type CreateOffer = TutorOffer | StudentTeachingApplication;

export function ContractCreatePage({ role = 'student' }: { role?: CreateRole }) {
  const params = useParams();
  const requestId = role === 'student' ? (params.studyingRequestId ?? '') : (params.requestId ?? '');
  const counterpartId = role === 'student' ? (params.tutorRequestId ?? '') : (params.applicationId ?? '');
  const navigate = useNavigate();
  const { user } = useAuth();
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (): Promise<{ request: CreateRequest; offer: CreateOffer }> => {
    if (role === 'student') {
      const [request, offer] = await Promise.all([
        getMyStudyingRequest(requestId),
        getTutorOffer(requestId, counterpartId)
      ]);
      return { request, offer };
    }

    const [request, applications] = await Promise.all([
      getMyTeachingRequest(requestId),
      getTeachingRequestApplications(requestId)
    ]);
    const offer = applications.find((item) => item.id === counterpartId);
    if (!offer) throw new Error('Không tìm thấy đề nghị của học viên.');
    if (offer.contractId) {
      throw new Error('Đề nghị này đã có hợp đồng. Vui lòng mở hợp đồng hiện tại thay vì tạo thêm.');
    }
    return { request, offer };
  }, [counterpartId, requestId, role]);

  const query = useResource(load);
  const request = query.data?.request ?? null;
  const offer = query.data?.offer ?? null;

  const submit = async (terms: ContractTerms) => {
    setSaving(true);
    try {
      if (role === 'student') {
        const created = await createStudentContract(requestId, counterpartId, terms);
        navigate(`/student/contracts/${created.id}`, { state: { notice: 'Đã tạo hợp đồng và gửi cho gia sư.' } });
      } else {
        const created = await createTeachingRequestContract(requestId, counterpartId, {
          ...terms,
          gradeId: (offer as StudentTeachingApplication).gradeId
        });
        navigate(`/tutor/sections/contracts/${(created as { id: string }).id}`, {
          state: { notice: 'Đã tạo hợp đồng và gửi cho học viên ký.' }
        });
      }
    } catch (caught) {
      notifyGlobalToast(contractCreationError(caught), 'error');
    } finally {
      setSaving(false);
    }
  };

  if (query.error)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!request || !offer)
    return (
      <div className="student-empty">
        <h3>Đang tải thông tin hợp đồng...</h3>
      </div>
    );

  const studentOffer = role === 'tutor' ? (offer as StudentTeachingApplication) : null;
  const tutorOffer = role === 'student' ? (offer as TutorOffer) : null;
  const studentName =
    role === 'student'
      ? displayName(user?.profile?.firstName, user?.profile?.lastName, user?.email, 'Học viên')
      : displayName(
          studentOffer?.studentFirstName,
          studentOffer?.studentLastName,
          studentOffer?.studentEmail,
          'Học viên'
        );
  const studentEmail =
    role === 'student' ? (user?.email ?? 'Chưa có email học viên') : (studentOffer?.studentEmail ?? '');
  const tutorName =
    role === 'student'
      ? displayName(tutorOffer?.tutorFirstName, tutorOffer?.tutorLastName, tutorOffer?.tutorEmail, 'Gia sư')
      : displayName(user?.profile?.firstName, user?.profile?.lastName, user?.email, 'Gia sư');
  const tutorEmail = role === 'student' ? (tutorOffer?.tutorEmail ?? '') : (user?.email ?? 'Chưa có email gia sư');
  const preferredSchedule =
    (role === 'student' ? tutorOffer?.preferredSchedule : studentOffer?.preferredSchedule) || request.preferredSchedule;
  const teachingMode = role === 'student' ? tutorOffer?.teachingMode : studentOffer?.learningMode;
  const gradeName = offer.gradeName;
  const subjectName =
    request.subjectName ||
    request.note ||
    ('customSubjectName' in request ? request.customSubjectName : null) ||
    'Môn học tùy chỉnh';
  const backPath =
    role === 'student' ? `/student/studying-requests/${requestId}` : `/tutor/sections/teaching-requests/${requestId}`;

  return (
    <>
      <button
        className="student-button secondary contract-create-back"
        type="button"
        onClick={() => navigate(backPath)}
      >
        ← Quay lại yêu cầu
      </button>
      <div className="student-page-heading">
        <div>
          <h1>Tạo hợp đồng</h1>
        </div>
      </div>
      <div className="student-contract-create-layout">
        <div className="student-contract-create-student">
          <ContractParticipantCard
            title="Thông tin học viên"
            icon="book"
            details={[
              {
                label: 'Họ và tên',
                value:
                  role === 'tutor' ? (
                    <ProfileNameLink role="student" profileId={studentOffer?.studentId}>
                      {studentName}
                    </ProfileNameLink>
                  ) : (
                    studentName
                  )
              },
              { label: 'Email', value: studentEmail },
              { label: 'Vai trò', value: 'Học viên' },
              {
                label: role === 'tutor' ? 'Đề nghị' : 'Yêu cầu học',
                value: (
                  <Link className="student-contract-source-tag" to={backPath}>
                    {role === 'tutor' ? 'Xem đề nghị' : 'Xem yêu cầu'}
                  </Link>
                )
              }
            ]}
          />
        </div>
        <div className="student-contract-create-tutor">
          <ContractParticipantCard
            title="Thông tin gia sư"
            icon="support"
            details={[
              {
                label: 'Họ và tên',
                value:
                  role === 'student' ? (
                    <ProfileNameLink role="tutor" profileId={tutorOffer?.tutorId}>
                      {tutorName}
                    </ProfileNameLink>
                  ) : (
                    tutorName
                  )
              },
              { label: 'Email', value: tutorEmail },
              { label: 'Vai trò', value: 'Gia sư' },
              {
                label: role === 'tutor' ? 'Yêu cầu dạy học' : 'Đề nghị dạy học',
                value: (
                  <Link className="student-contract-source-tag" to={backPath}>
                    {role === 'tutor' ? 'Xem yêu cầu' : 'Xem đề nghị'}
                  </Link>
                )
              }
            ]}
          />
        </div>
        <section className="student-panel student-contract-create-terms">
          <h2 className="student-contract-panel-heading">
            <Icon name="contract" size={16} /> Điều khoản hợp đồng
          </h2>
          <p className="student-contract-create-help">
            Kiểm tra thông tin cố định và thiết lập các điều khoản cho thỏa thuận này.
          </p>
          <div className="student-contract-fixed-terms">
            <ReadOnlyTerm label="Môn học" value={subjectName} />
            <ReadOnlyTerm label="Lớp" value={gradeName} />
            <ReadOnlyTerm label="Hình thức dạy" value={teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
          </div>
          <div className="contract-create-terms-note">
            <strong>Điều khoản hợp đồng</strong>
            <ul>
              <li>Đồng ý với các điều khoản đã ghi trong hợp đồng.</li>
              <li>Nghỉ học phải thông báo trước; hai bên có thể đổi lịch hoặc trao đổi về hoàn tiền.</li>
              <li>
                Các buổi học cần được thanh toán trước khi bắt đầu. Với chu kỳ thanh toán theo tháng, học phí phải được
                thanh toán trước khi buổi học đầu tiên của tháng đó bắt đầu.
              </li>
              <li>Học phí của buổi đã được xác nhận tham gia không được hoàn lại.</li>
              <li>Hai bên có thể khiếu nại hoặc yêu cầu dừng hợp đồng khi đã thỏa thuận.</li>
              <li>Buổi học bị đánh dấu đã dạy nhưng thực tế không diễn ra có thể được yêu cầu hoàn tiền.</li>
            </ul>
            <a href="/contract-terms.pdf" target="_blank" rel="noreferrer">
              Mở chi tiết điều khoản hợp đồng (PDF)
            </a>
          </div>
          <ContractTermsForm
            editableScheduleList
            requirePositivePrice={role === 'student'}
            validateScheduleOverlap={role === 'student'}
            loading={saving}
            submitLabel="Tạo và ký hợp đồng"
            includeAgreement
            pdfDetails={{
              studentName,
              studentEmail,
              tutorName,
              tutorEmail,
              studentPhone: role === 'student' ? user?.profile?.phone : undefined,
              studentAddress:
                teachingMode === 'OFFLINE' && role === 'student'
                  ? (user?.profile?.detailAddress ?? user?.profile?.address?.detailAddress)
                  : null,
              tutorPhone: role === 'tutor' ? user?.profile?.phone : undefined,
              tutorAddress: teachingMode === 'OFFLINE' && role === 'tutor' ? request.detailAddress : null,
              subject: subjectName,
              grade: gradeName,
              teachingMode: teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'
            }}
            initial={{
              price: offer.proposedPrice,
              startDate: todayValue(),
              endDate: nextMonthValue(),
              schedules: findCommonSchedules(request.availabilities, preferredSchedule)
            }}
            onCancel={() => navigate(backPath)}
            onCheckSchedule={
              role === 'student'
                ? (terms) =>
                    checkStudentContractSchedule(requestId, counterpartId, terms).then((result) => result.messages)
                : (terms) =>
                    checkTeachingRequestContractSchedule(requestId, counterpartId, {
                      ...terms,
                      gradeId: (offer as StudentTeachingApplication).gradeId
                    }).then((result) => result.messages)
            }
            onSubmit={submit}
          />
        </section>
      </div>
    </>
  );
}

function contractCreationError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'DUPLICATE_CONTRACT') {
      return 'Đề nghị này đã có hợp đồng chưa hủy. Không thể tạo thêm hợp đồng.';
    }
    if (error.code === 'INVALID_CONTRACT_APPLICATION_STATUS') {
      return 'Chỉ có thể tạo hợp đồng từ đề nghị đã được chấp nhận.';
    }
    if (error.code === 'INVALID_CONTRACT_SOURCE_STATUS') {
      return 'Yêu cầu đã thay đổi trạng thái, không thể tạo hợp đồng.';
    }
    if (error.code === 'LESSON_SCHEDULE_CONFLICT') {
      return `${error.message} Bạn có thể điều chỉnh lại các khung giờ trước khi tạo hợp đồng.`;
    }
    if (error.code === 'OVERLAPPING_CONTRACT_SCHEDULE') {
      return 'Các khung giờ trong cùng một ngày không được trùng hoặc chồng lên nhau.';
    }
    if (error.code === 'INVALID_CONTRACT_PRICE') {
      return 'Giá mỗi buổi học phải lớn hơn 0.';
    }
    if (error.code === 'UNSUPPORTED_PAYMENT_PERIOD') {
      return 'Hợp đồng chỉ hỗ trợ thanh toán hàng tháng hoặc theo gói.';
    }
    if (error.message && error.message !== 'An unexpected error occurred') return error.message;
  }
  if (error instanceof Error && error.message.trim()) return error.message;
  return 'Không thể tạo hợp đồng. Vui lòng thử lại.';
}

function ReadOnlyTerm({ label, value }: { label: string; value: string | null | undefined }) {
  return (
    <div className="student-contract-fixed-term">
      <span>{label}</span>
      <strong>{value || 'Chưa có thông tin'}</strong>
    </div>
  );
}

function displayName(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined,
  fallback: string
) {
  return [firstName, lastName].filter(Boolean).join(' ') || email || fallback;
}

function findCommonSchedules(
  requestSchedules: { dayOfWeek: number; startTime: string; endTime: string }[],
  preferredSchedule: string | null | undefined
) {
  const parsed = parseSchedule(preferredSchedule);
  if (!requestSchedules.length) return parsed;
  if (!parsed.length)
    return requestSchedules.map((schedule) => ({
      ...schedule,
      startTime: schedule.startTime.slice(0, 5),
      endTime: schedule.endTime.slice(0, 5)
    }));
  const commonSchedules = requestSchedules.flatMap((requestSchedule) =>
    parsed
      .filter((item) => item.dayOfWeek === requestSchedule.dayOfWeek)
      .map((item) => {
        const startTime =
          requestSchedule.startTime.slice(0, 5) > item.startTime
            ? requestSchedule.startTime.slice(0, 5)
            : item.startTime;
        const endTime =
          requestSchedule.endTime.slice(0, 5) < item.endTime ? requestSchedule.endTime.slice(0, 5) : item.endTime;
        return endTime > startTime ? { dayOfWeek: requestSchedule.dayOfWeek, startTime, endTime } : null;
      })
      .filter((item): item is { dayOfWeek: number; startTime: string; endTime: string } => item !== null)
  );
  return commonSchedules.length ? commonSchedules : parsed;
}

function parseSchedule(value: string | null | undefined) {
  if (!value) return [];
  const normalized = value
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
  const robustTime = normalized.match(/(\d{1,2}:\d{2})\s*(?:-|to|\u2013)\s*(\d{1,2}:\d{2})/i);
  const robustDays = [
    ['thu hai', 1],
    ['thu ba', 2],
    ['thu tu', 3],
    ['thu nam', 4],
    ['thu sau', 5],
    ['thu bay', 6],
    ['chu nhat', 7],
    ['monday', 1],
    ['tuesday', 2],
    ['wednesday', 3],
    ['thursday', 4],
    ['friday', 5],
    ['saturday', 6],
    ['sunday', 7]
  ] as const;
  if (robustTime) {
    const days = robustDays.filter(([label]) => normalized.includes(label)).map(([, day]) => day);
    const startTime = normalizeTime(robustTime[1]);
    const endTime = normalizeTime(robustTime[2]);
    if (days.length && startTime && endTime && endTime > startTime) {
      return [...new Set(days)].map((dayOfWeek) => ({ dayOfWeek, startTime, endTime }));
    }
  }
  const timeMatch = value.match(/(\d{1,2}:\d{2})\s*(?:-|to|–)\s*(\d{1,2}:\d{2})/i);
  const dayTokens = [
    ['thứ hai', 1],
    ['thứ ba', 2],
    ['thứ tư', 3],
    ['thứ năm', 4],
    ['thứ sáu', 5],
    ['thứ bảy', 6],
    ['chủ nhật', 7],
    ['monday', 1],
    ['tuesday', 2],
    ['wednesday', 3],
    ['thursday', 4],
    ['friday', 5],
    ['saturday', 6],
    ['sunday', 7]
  ] as const;
  if (timeMatch) {
    const days = dayTokens.filter(([label]) => value.toLowerCase().includes(label)).map(([, day]) => day);
    if (days.length) {
      const startTime = normalizeTime(timeMatch[1]);
      const endTime = normalizeTime(timeMatch[2]);
      if (startTime && endTime && endTime > startTime) {
        return [...new Set(days)].map((dayOfWeek) => ({ dayOfWeek, startTime, endTime }));
      }
    }
  }
  return [];
  /*
  const days: Record<string, number> = { mon: 1, monday: 1, 'thứ hai': 1, tue: 2, tues: 2, tuesday: 2, 'thứ ba': 2, wed: 3, wednesday: 3, 'thứ tư': 3, thu: 4, thur: 4, thurs: 4, thursday: 4, 'thứ năm': 4, fri: 5, friday: 5, 'thứ sáu': 5, sat: 6, saturday: 6, 'thứ bảy': 6, sun: 7, sunday: 7, 'chủ nhật': 7 };
  const matchedDays = [...value.toLowerCase().matchAll(/(mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?|thứ hai|thứ ba|thứ tư|thứ năm|thứ sáu|thứ bảy|chủ nhật)/g)]
    .map((match) => days[match[1]])
    .filter((day): day is number => Boolean(day));
  const range = value.match(/(\\d{1,2}:\\d{2})\\s*(?:-|–|to)\\s*(\\d{1,2}:\\d{2})/i);
  if (!matchedDays.length || !range) return [];
  const startTime = normalizeTime(range[1]);
  const endTime = normalizeTime(range[2]);
  if (!startTime || !endTime || endTime <= startTime) return [];
  return [...new Set(matchedDays)].map((dayOfWeek) => ({ dayOfWeek, startTime, endTime })); */
}

function normalizeTime(value: string) {
  const [hour, minute] = value.split(':');
  return Number(hour) > 23 || Number(minute) > 59 ? '' : `${hour.padStart(2, '0')}:${minute}`;
}

function todayValue() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
}

function nextMonthValue() {
  const date = new Date();
  date.setMonth(date.getMonth() + 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
