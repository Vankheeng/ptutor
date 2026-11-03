import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  createRefundRequest,
  getStudentContractLessons,
  type AbsenceOutcome,
  type EvidenceInput
} from '../../services/refundApi';
import { RefundEvidenceField } from '../../components/refund/RefundEvidenceField';
import {
  confirmationDeadline,
  formatClockTime,
  formatLessonDate,
  refundErrorMessage,
  timeLeft
} from '../../components/refund/refundFormat';
import '../../components/refund/refund.css';

const DESCRIPTION_LIMIT = 2000;
const claimOptions: { value: AbsenceOutcome; label: string }[] = [
  { value: 'TUTOR_ABSENT', label: 'Gia sư không dạy' },
  { value: 'STUDENT_ABSENT', label: 'Tôi nghỉ buổi này' }
];

export function StudentRefundRequestCreatePage() {
  const { contractId = '' } = useParams();
  const navigate = useNavigate();
  const [claims, setClaims] = useState<Record<string, AbsenceOutcome>>({});
  const [description, setDescription] = useState('');
  const [evidences, setEvidences] = useState<EvidenceInput[]>([]);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const load = useCallback(
    async (signal: AbortSignal) =>
      (await getStudentContractLessons(contractId, signal)).filter(
        (lesson) => lesson.status === 'PENDING_CONFIRMATION'
      ),
    [contractId]
  );
  const { data: pending, error, isLoading } = useResource(load);

  const chosen = Object.entries(claims);
  const lessonsError = tried && chosen.length === 0;
  const descriptionError = tried && !description.trim();

  const toggle = (lessonId: string, value: AbsenceOutcome) =>
    setClaims((current) => {
      const next = { ...current };
      if (next[lessonId] === value) delete next[lessonId];
      else next[lessonId] = value;
      return next;
    });

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTried(true);
    if (chosen.length === 0 || !description.trim()) return;
    setBusy(true);
    setFailure(null);
    try {
      const created = await createRefundRequest({
        contractId,
        description: description.trim(),
        lessons: chosen.map(([lessonId, claimedOutcome]) => ({ lessonId, claimedOutcome })),
        evidences
      });
      navigate(`/student/refund-requests/${created.id}`, { state: { created: true } });
    } catch (submitError) {
      setFailure(refundErrorMessage(submitError));
      setBusy(false);
    }
  };

  return (
    <form className="rf-page" onSubmit={(event) => void submit(event)} noValidate>
      <Link className="rf-back" to={`/student/contracts/${contractId}/lessons`}>
        ‹ Buổi học
      </Link>
      <h1>Yêu cầu hoàn tiền</h1>
      {failure && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {failure}
        </div>
      )}
      {error && (
        <div className="rf-alert rf-alert-danger" role="alert">
          {error}
        </div>
      )}

      <section className="rf-card">
        <h2>
          Buổi học không diễn ra <span className="rf-required">*</span>
        </h2>
        {isLoading && !pending && <div className="rf-state">Đang tải…</div>}
        {pending && pending.length === 0 && <div className="rf-state">Không có buổi nào đang chờ xác nhận.</div>}
        {pending && pending.length > 0 && (
          <div className="rf-rows">
            {pending.map((lesson) => {
              const deadline = confirmationDeadline(lesson.markedTaughtAt);
              return (
                <div className="rf-row" key={lesson.id}>
                  <div className="rf-row-main">
                    <strong>{formatLessonDate(lesson.date, lesson.startTime, lesson.endTime)}</strong>
                    <small>
                      Gia sư báo đã dạy lúc {formatClockTime(lesson.markedTaughtAt)}
                      {deadline && ` · ${timeLeft(deadline)}`}
                    </small>
                  </div>
                  <div className="rf-seg" role="group" aria-label={`Nguyên nhân buổi ${lesson.date}`}>
                    {claimOptions.map((option) => (
                      <button
                        key={option.value}
                        type="button"
                        aria-pressed={claims[lesson.id] === option.value}
                        onClick={() => toggle(lesson.id, option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
        {lessonsError && <span className="rf-error">Chọn ít nhất một buổi và nguyên nhân.</span>}
      </section>

      <section className="rf-card">
        <div className="rf-field">
          <label htmlFor="rf-description">
            Mô tả <span className="rf-required">*</span>
          </label>
          <textarea
            id="rf-description"
            rows={5}
            maxLength={DESCRIPTION_LIMIT}
            value={description}
            aria-invalid={descriptionError}
            onChange={(event) => setDescription(event.target.value)}
          />
          <div className="rf-help">
            <span className="rf-error">{descriptionError ? 'Vui lòng mô tả sự việc.' : ''}</span>
            <span>
              {description.length}/{DESCRIPTION_LIMIT}
            </span>
          </div>
        </div>
        <RefundEvidenceField value={evidences} onChange={setEvidences} />
      </section>

      <div className="rf-actions">
        <Link className="rf-button secondary" to={`/student/contracts/${contractId}/lessons`}>
          Huỷ
        </Link>
        <button className="rf-button" type="submit" disabled={busy || pending?.length === 0}>
          Gửi yêu cầu
        </button>
      </div>
    </form>
  );
}
