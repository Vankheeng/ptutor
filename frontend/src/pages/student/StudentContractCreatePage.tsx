import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { ApiError } from '../../services/httpClient';
import {
  createStudentContract,
  getMyStudyingRequest,
  getTutorOffer,
  type ContractTerms,
  type StudyingRequest,
  type TutorOffer
} from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { Icon } from '../../components/ui/Icon';
import { StudentContractParticipantCard } from '../../components/student/StudentContractParticipantCard';
import { StudentContractTermsForm } from '../../components/student/StudentContractTermsForm';

export function StudentContractCreatePage() {
  const { studyingRequestId = '', tutorRequestId = '' } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const load = useCallback(async () => {
    const [request, offer] = await Promise.all([
      getMyStudyingRequest(studyingRequestId),
      getTutorOffer(studyingRequestId, tutorRequestId)
    ]);
    return { request, offer };
  }, [studyingRequestId, tutorRequestId]);
  const query = useResource(load);
  const request: StudyingRequest | null = query.data?.request ?? null;
  const offer: TutorOffer | null = query.data?.offer ?? null;

  const submit = async (terms: ContractTerms) => {
    setSaving(true);
    setError('');
    try {
      const created = await createStudentContract(studyingRequestId, tutorRequestId, terms);
      navigate(`/student/contracts/${created.id}`, { state: { notice: 'Đã tạo hợp đồng và gửi cho gia sư.' } });
    } catch (caught) {
      setError(caught instanceof ApiError || caught instanceof Error ? caught.message : 'Không thể tạo hợp đồng.');
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
        <h3>Đang tải thông tin hợp đồng…</h3>
      </div>
    );

  return (
    <>
      <Link className="student-link" to={`/student/studying-requests/${studyingRequestId}`}>
        ← Quay lại yêu cầu tìm gia sư
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">TẠO HỢP ĐỒNG</span>
          <h1>Tạo hợp đồng</h1>
          <p>Điền thông tin hợp đồng để tạo thỏa thuận mới với gia sư.</p>
        </div>
      </div>
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <div className="student-contract-create-layout">
        <div className="student-contract-create-student">
          <StudentContractParticipantCard
            title="Thông tin học viên"
            icon="book"
            details={[
              {
                label: 'Họ và tên',
                value: getDisplayName(user?.profile?.firstName, user?.profile?.lastName, user?.email, 'Học viên')
              },
              { label: 'Email', value: user?.email ?? 'Chưa có email học viên' },
              { label: 'Vai trò', value: 'Học viên' },
              {
                label: 'Yêu cầu tìm gia sư',
                value: (
                  <Link className="student-contract-source-tag" to={`/student/studying-requests/${studyingRequestId}`}>
                    {request.title}
                  </Link>
                )
              }
            ]}
          />
        </div>

        <div className="student-contract-create-tutor">
          <StudentContractParticipantCard
            title="Thông tin gia sư"
            icon="support"
            details={[
              {
                label: 'Họ và tên',
                value: getDisplayName(offer.tutorFirstName, offer.tutorLastName, offer.tutorEmail, 'Gia sư')
              },
              { label: 'Email', value: offer.tutorEmail },
              { label: 'Vai trò', value: 'Gia sư' },
              {
                label: 'Đề nghị dạy học',
                value: (
                  <Link
                    className="student-contract-source-tag"
                    to={`/student/studying-requests/${studyingRequestId}#tutor-offers`}
                  >
                    Đề nghị đã chấp nhận
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
            <ReadOnlyTerm label="Môn học" value={request.subjectName || request.note || 'Môn học tùy chỉnh'} />
            <ReadOnlyTerm label="Lớp" value={offer.gradeName} />
            <ReadOnlyTerm label="Hình thức dạy" value={offer.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'} />
          </div>
          <StudentContractTermsForm
            loading={saving}
            submitLabel="Tạo hợp đồng"
            initial={{
              price: offer.proposedPrice,
              schedules: findCommonSchedules(request.availabilities, offer.preferredSchedule)
            }}
            includePackage={false}
            onCancel={() => navigate(`/student/studying-requests/${studyingRequestId}`)}
            onSubmit={submit}
          />
        </section>
      </div>
    </>
  );
}

function ReadOnlyTerm({ label, value }: { label: string; value: string }) {
  return (
    <div className="student-contract-fixed-term">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function getDisplayName(
  firstName: string | null | undefined,
  lastName: string | null | undefined,
  email: string | null | undefined,
  fallback: string
) {
  return [firstName, lastName].filter(Boolean).join(' ') || email || fallback;
}

interface TimeRange {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

function findCommonSchedules(
  studentSchedules: { dayOfWeek: number; startTime: string; endTime: string }[],
  tutorScheduleText: string | null
) {
  const tutorSchedules = parseTutorSchedule(tutorScheduleText);
  return studentSchedules.flatMap((studentSchedule) =>
    tutorSchedules
      .filter((tutorSchedule) => tutorSchedule.dayOfWeek === studentSchedule.dayOfWeek)
      .map((tutorSchedule) => {
        const startTime =
          studentSchedule.startTime.slice(0, 5) > tutorSchedule.startTime
            ? studentSchedule.startTime.slice(0, 5)
            : tutorSchedule.startTime;
        const endTime =
          studentSchedule.endTime.slice(0, 5) < tutorSchedule.endTime
            ? studentSchedule.endTime.slice(0, 5)
            : tutorSchedule.endTime;
        return endTime > startTime ? { dayOfWeek: studentSchedule.dayOfWeek, startTime, endTime } : null;
      })
      .filter((schedule): schedule is TimeRange => schedule !== null)
  );
}

function parseTutorSchedule(value: string | null): TimeRange[] {
  if (!value) return [];
  const daysByName: Record<string, number> = {
    mon: 1,
    monday: 1,
    tue: 2,
    tues: 2,
    tuesday: 2,
    wed: 3,
    wednesday: 3,
    thu: 4,
    thur: 4,
    thurs: 4,
    thursday: 4,
    fri: 5,
    friday: 5,
    sat: 6,
    saturday: 6,
    sun: 7,
    sunday: 7
  };
  const matchedDays = [
    ...value
      .toLowerCase()
      .matchAll(
        /\b(mon(?:day)?|tue(?:s|sday)?|wed(?:nesday)?|thu(?:r|rs|rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b/g
      )
  ]
    .map((match) => daysByName[match[1]])
    .filter((day): day is number => Boolean(day));
  const range = value.match(/(\d{1,2}:\d{2})\s*(?:-|–|to)\s*(\d{1,2}:\d{2})/i);
  if (!matchedDays.length || !range) return [];
  const startTime = normalizeTime(range[1]);
  const endTime = normalizeTime(range[2]);
  if (!startTime || !endTime || endTime <= startTime) return [];
  return [...new Set(matchedDays)].map((dayOfWeek) => ({ dayOfWeek, startTime, endTime }));
}

function normalizeTime(value: string) {
  const [hour, minute] = value.split(':');
  if (Number(hour) > 23 || Number(minute) > 59) return '';
  return `${hour.padStart(2, '0')}:${minute}`;
}
