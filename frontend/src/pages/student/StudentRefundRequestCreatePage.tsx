import { useCallback, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  createRefundRequest,
  getStudentContractLessons,
  type AbsenceOutcome,
  type EvidenceInput
} from '../../services/refundApi';
import { StudentRefundEvidenceField } from '../../components/student/lesson/StudentRefundEvidenceField';
import {
  confirmationDeadline,
  formatClockTime,
  formatLessonDate,
  refundErrorMessage,
  timeLeft
} from '../../components/refund/refundFormat';
import { getContracts, type Contract } from '../../services/studentApi';
import '../../components/refund/refund.css';

const DESCRIPTION_LIMIT = 2000;
const claimOptions: { value: AbsenceOutcome; label: string }[] = [
  { value: 'TUTOR_ABSENT', label: 'Gia sư không dạy' },
  { value: 'STUDENT_ABSENT', label: 'Tôi nghỉ buổi này' },
  { value: 'TUTOR_LATE_OR_SHORT', label: 'Gia sư dạy muộn hoặc thiếu giờ' }
];

export function StudentRefundRequestCreatePage() {
  const { contractId: routeContractId = '' } = useParams();
  const navigate = useNavigate();
  const [selectedContractId, setSelectedContractId] = useState(routeContractId);
  const [claims, setClaims] = useState<Record<string, { outcome: AbsenceOutcome; missedMinutes: string }>>({});
  const [description, setDescription] = useState('');
  const [evidences, setEvidences] = useState<EvidenceInput[]>([]);
  const [tried, setTried] = useState(false);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const activeContractId = routeContractId || selectedContractId;
  const contractsLoad = useCallback((signal: AbortSignal) => getContracts(undefined, 0, 100, signal), []);
  const { data: contracts } = useResource(contractsLoad);

  const load = useCallback(
    async (signal: AbortSignal) => {
      if (!activeContractId) return [];
      return (await getStudentContractLessons(activeContractId, signal)).filter(
        (lesson) => lesson.status === 'PENDING_CONFIRMATION'
      );
    },
    [activeContractId]
  );
  const { data: pending, error, isLoading } = useResource(load);

  const chosen = Object.entries(claims);
  const lessonsError = tried && chosen.length !== 1;
  const descriptionError = tried && !description.trim();

  const toggle = (lessonId: string, value: AbsenceOutcome) =>
    setClaims((current) => {
      if (current[lessonId]?.outcome === value) return {};
      return { [lessonId]: { outcome: value, missedMinutes: '' } };
    });

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTried(true);
    const invalidMinutes = chosen.some(([lessonId, claim]) => {
      if (claim.outcome !== 'TUTOR_LATE_OR_SHORT') return false;
      const lesson = pending?.find((item) => item.id === lessonId);
      if (!lesson) return true;
      const duration =
        (new Date(`2000-01-01T${lesson.endTime}`).getTime() - new Date(`2000-01-01T${lesson.startTime}`).getTime()) /
        60000;
      const minutes = Number(claim.missedMinutes);
      return !Number.isInteger(minutes) || minutes < 1 || minutes >= duration;
    });
    if (chosen.length !== 1 || !description.trim() || invalidMinutes) return;
    setBusy(true);
    setFailure(null);
    try {
      const created = await createRefundRequest({
        contractId: activeContractId,
        description: description.trim(),
        lessons: chosen.map(([lessonId, claim]) => ({
          lessonId,
          claimedOutcome: claim.outcome,
          missedMinutes: claim.outcome === 'TUTOR_LATE_OR_SHORT' ? Number(claim.missedMinutes) : null
        })),
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
      <Link
        className="rf-back"
        to={activeContractId ? `/student/contracts/${activeContractId}/lessons` : '/student/refund-requests'}
      >
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

      {!routeContractId && (
        <section className="rf-card rf-contract-picker">
          <div className="rf-field">
            <label htmlFor="refund-contract">Chọn hợp đồng cần hoàn tiền</label>
            <select
              id="refund-contract"
              value={selectedContractId}
              onChange={(event) => {
                setSelectedContractId(event.target.value);
                setClaims({});
                setTried(false);
              }}
            >
              <option value="">Chọn hợp đồng</option>
              {(contracts?.content ?? []).map((contract: Contract) => (
                <option key={contract.id} value={contract.id}>
                  {contract.subjectName} · {contract.gradeName} · {contract.tutorFirstName} {contract.tutorLastName}
                </option>
              ))}
            </select>
          </div>
          {!activeContractId && (
            <div className="rf-state">Chọn hợp đồng để xem các buổi học có thể yêu cầu hoàn tiền.</div>
          )}
        </section>
      )}

      <section className="rf-card">
        <h2>
          Buổi học cần hoàn tiền <span className="rf-required">*</span>
        </h2>
        <p className="rf-kicker">Mỗi yêu cầu chỉ áp dụng cho một buổi học.</p>
        {activeContractId && isLoading && !pending && <div className="rf-state">Đang tải…</div>}
        {activeContractId && pending && pending.length === 0 && (
          <div className="rf-state">Không có buổi nào đang chờ xác nhận.</div>
        )}
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
                        aria-pressed={claims[lesson.id]?.outcome === option.value}
                        onClick={() => toggle(lesson.id, option.value)}
                      >
                        {option.label}
                      </button>
                    ))}
                  </div>
                  {claims[lesson.id]?.outcome === 'TUTOR_LATE_OR_SHORT' && (
                    <div className="rf-field">
                      <label htmlFor={`missed-${lesson.id}`}>
                        Số phút gia sư dạy thiếu <span className="rf-required">*</span>
                      </label>
                      <small>Số tiền hoàn dự kiến được tính theo tỷ lệ phút bị thiếu trên thời lượng buổi học.</small>
                      <input
                        id={`missed-${lesson.id}`}
                        type="number"
                        min="1"
                        max={Math.max(
                          1,
                          (new Date(`2000-01-01T${lesson.endTime}`).getTime() -
                            new Date(`2000-01-01T${lesson.startTime}`).getTime()) /
                            60000 -
                            1
                        )}
                        value={claims[lesson.id].missedMinutes}
                        onChange={(event) =>
                          setClaims((current) => ({
                            ...current,
                            [lesson.id]: { ...current[lesson.id], missedMinutes: event.target.value }
                          }))
                        }
                      />
                      {tried &&
                        (!claims[lesson.id].missedMinutes ||
                          Number(claims[lesson.id].missedMinutes) < 1 ||
                          Number(claims[lesson.id].missedMinutes) >=
                            (new Date(`2000-01-01T${lesson.endTime}`).getTime() -
                              new Date(`2000-01-01T${lesson.startTime}`).getTime()) /
                              60000) && (
                          <span className="rf-error">Nhập số phút bị thiếu, nhỏ hơn thời lượng buổi học.</span>
                        )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
        {lessonsError && <span className="rf-error">Vui lòng chọn đúng một buổi học và nguyên nhân.</span>}
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
        <StudentRefundEvidenceField value={evidences} onChange={setEvidences} />
      </section>

      <div className="rf-actions">
        <Link
          className="rf-button secondary"
          to={activeContractId ? `/student/contracts/${activeContractId}/lessons` : '/student/refund-requests'}
        >
          Huỷ
        </Link>
        <button className="rf-button" type="submit" disabled={busy || pending?.length === 0}>
          Gửi yêu cầu
        </button>
      </div>
    </form>
  );
}
