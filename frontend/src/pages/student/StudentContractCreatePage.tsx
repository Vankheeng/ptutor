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
      navigate(`/student/contracts/${created.id}`, { state: { notice: 'Contract created and sent to the tutor.' } });
    } catch (caught) {
      setError(
        caught instanceof ApiError || caught instanceof Error ? caught.message : 'Could not create the contract.'
      );
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
        <h3>Loading contract details…</h3>
      </div>
    );

  return (
    <>
      <Link className="student-link" to={`/student/studying-requests/${studyingRequestId}`}>
        ← Back to studying request
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">CREATE CONTRACT</span>
          <h1>Create contract</h1>
          <p>Fill in the contract details to create a new agreement with the tutor.</p>
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
            title="Student information"
            icon="book"
            details={[
              {
                label: 'Full name',
                value: getDisplayName(user?.profile?.firstName, user?.profile?.lastName, user?.email, 'Student')
              },
              { label: 'Email', value: user?.email ?? 'Student email unavailable' },
              { label: 'Role', value: 'Student' },
              {
                label: 'Studying request',
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
            title="Tutor information"
            icon="support"
            details={[
              {
                label: 'Full name',
                value: getDisplayName(offer.tutorFirstName, offer.tutorLastName, offer.tutorEmail, 'Tutor')
              },
              { label: 'Email', value: offer.tutorEmail },
              { label: 'Role', value: 'Tutor' },
              {
                label: 'Proposal teaching',
                value: (
                  <Link
                    className="student-contract-source-tag"
                    to={`/student/studying-requests/${studyingRequestId}#tutor-offers`}
                  >
                    Accepted proposal
                  </Link>
                )
              }
            ]}
          />
        </div>

        <section className="student-panel student-contract-create-terms">
          <h2 className="student-contract-panel-heading">
            <Icon name="contract" size={16} /> Contract terms
          </h2>
          <p className="student-contract-create-help">
            Review the fixed information and define the terms for this agreement.
          </p>
          <div className="student-contract-fixed-terms">
            <ReadOnlyTerm label="Subject" value={request.subjectName || request.note || 'Custom subject'} />
            <ReadOnlyTerm label="Grade" value={offer.gradeName} />
            <ReadOnlyTerm label="Teaching mode" value={offer.teachingMode === 'ONLINE' ? 'Online' : 'In person'} />
          </div>
          <StudentContractTermsForm
            loading={saving}
            submitLabel="Create contract"
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
