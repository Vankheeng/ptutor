import { useCallback, useMemo, useState } from 'react';
import { useResource } from '../../../hooks/useResource';
import {
  approveRequest,
  getAdminStudyingRequest,
  getAdminStudyingRequests,
  getAdminSubjects,
  getAdminTeachingRequest,
  getAdminTeachingRequests,
  rejectRequest,
  type ReviewKind,
  type SubjectResolution
} from '../../../services/adminRequestReviewApi';
import type { Subject } from '../../../types/api';
import { formatDateTime } from '../adminFormat';
import {
  formatAvailability,
  fromStudyingRequest,
  fromTeachingRequest,
  normalizeName,
  reviewErrorMessage,
  reviewKindLabels,
  reviewStatusMeta,
  suggestSubject,
  type ReviewItem
} from '../requestReviewFormat';
import { AdminAvatar, AdminModal, AdminState, DefinitionGrid, ReasonField, StatusBadge } from './AdminUi';

type Step = 'resolve' | 'reject' | 'create';
type Action = SubjectResolution['action'];

interface Props {
  kind: ReviewKind;
  id: string;
  onClose: () => void;
  onReviewed: (message: string) => void;
}

export function RequestReviewModal({ kind, id, onClose, onReviewed }: Props) {
  const loadRequest = useCallback(
    async (signal: AbortSignal): Promise<ReviewItem> =>
      kind === 'teaching'
        ? fromTeachingRequest(await getAdminTeachingRequest(id, signal))
        : fromStudyingRequest(await getAdminStudyingRequest(id, signal)),
    [id, kind]
  );
  const { data: item, error: loadError, isLoading } = useResource(loadRequest);
  const { data: subjects } = useResource(getAdminSubjects);

  const [step, setStep] = useState<Step>('resolve');
  const [chosenAction, setChosenAction] = useState<Action | null>(null);
  const [chosenSubjectId, setChosenSubjectId] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [showReasonError, setShowReasonError] = useState(false);
  const [newName, setNewName] = useState<string | null>(null);
  const [newDescription, setNewDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const suggestion = useMemo(
    () => (item?.customSubject && subjects ? suggestSubject(item.customSubject, subjects) : null),
    [item, subjects]
  );
  const action: Action = chosenAction ?? (suggestion ? 'USE_EXISTING' : 'CREATE');
  const subjectId = chosenSubjectId ?? suggestion?.id ?? null;
  const subjectName = newName ?? item?.customSubject ?? '';

  const submit = async (run: () => Promise<unknown>, message: string) => {
    setSubmitting(true);
    setError('');
    try {
      await run();
      onReviewed(message);
    } catch (caught) {
      setError(reviewErrorMessage(caught));
      setSubmitting(false);
    }
  };

  if (!item) {
    return (
      <AdminModal title="Chi tiết yêu cầu" onClose={onClose}>
        <div className="admin-modal-pad">
          <AdminState loading={isLoading} error={loadError} />
        </div>
      </AdminModal>
    );
  }

  const pending = item.status === 'PENDING_REVIEW';
  const needsResolution = pending && Boolean(item.customSubject);

  const approve = () => {
    if (needsResolution && action === 'CREATE') {
      setStep('create');
      setError('');
      return;
    }
    const resolution: SubjectResolution | undefined =
      needsResolution && subjectId ? { action: 'USE_EXISTING', subjectId } : undefined;
    void submit(() => approveRequest(kind, item.id, resolution), `Đã duyệt “${item.title}”.`);
  };

  const reject = () => {
    if (!reason.trim()) {
      setShowReasonError(true);
      return;
    }
    void submit(() => rejectRequest(kind, item.id, reason.trim()), `Đã từ chối “${item.title}”.`);
  };

  if (step === 'create') {
    return (
      <AdminModal
        title="Tạo môn học mới"
        subtitle={`Từ ${reviewKindLabels[kind].toLowerCase()}: ${item.title}`}
        size="sm"
        onClose={onClose}
      >
        <CreateSubjectStep
          item={item}
          subjects={subjects ?? []}
          name={subjectName}
          description={newDescription}
          submitting={submitting}
          error={error}
          onNameChange={setNewName}
          onDescriptionChange={setNewDescription}
          onBack={() => {
            setStep('resolve');
            setError('');
          }}
          onSubmit={() =>
            void submit(
              () =>
                approveRequest(kind, item.id, {
                  action: 'CREATE',
                  name: subjectName.trim(),
                  description: newDescription.trim() || undefined
                }),
              `Đã tạo môn “${subjectName.trim()}” và duyệt “${item.title}”.`
            )
          }
        />
      </AdminModal>
    );
  }

  return (
    <AdminModal
      title={item.title}
      subtitle={
        <>
          <span className="admin-badge admin-badge-brand">{reviewKindLabels[kind]}</span>
          <StatusBadge meta={reviewStatusMeta[item.status]} />
          <span>Gửi lúc {formatDateTime(item.createdAt)}</span>
        </>
      }
      onClose={onClose}
    >
      <div className="review-layout">
        <div className="review-main admin-stack-gap">
          <div className="review-person">
            <AdminAvatar name={item.personName} url={item.personAvatarUrl} />
            <div className="admin-stack">
              <small>{item.personLabel}</small>
              <strong>{item.personName}</strong>
              <a className="admin-link" href={`mailto:${item.personEmail}`}>
                {item.personEmail}
              </a>
            </div>
          </div>
          {item.description && <ReviewText label="Mô tả" value={item.description} />}
          {item.goals && <ReviewText label="Mục tiêu học tập" value={item.goals} />}
          {item.adminNote && (
            <div className="review-admin-note">
              <strong>Ghi chú gửi admin</strong>
              <p>{item.adminNote}</p>
            </div>
          )}
          <DefinitionGrid items={item.facts} />
          {item.availabilities.length > 0 && (
            <div className="admin-stack-gap review-slots">
              <span className="review-label">Khung giờ</span>
              <div className="admin-tags">
                {item.availabilities.map((slot) => (
                  <span key={`${slot.dayOfWeek}-${slot.startTime}`}>{formatAvailability(slot)}</span>
                ))}
              </div>
            </div>
          )}
        </div>

        <aside className="review-side admin-stack-gap">
          {error && (
            <div className="admin-alert admin-alert-danger" role="alert">
              {error}
            </div>
          )}

          {pending && step === 'resolve' && (
            <>
              {needsResolution ? (
                <>
                  <div className="admin-alert admin-alert-warning">
                    <span>
                      Môn tự nhập, chưa có trong danh mục:
                      <strong className="review-custom-subject">“{item.customSubject}”</strong>
                    </span>
                  </div>
                  <fieldset className="review-choices">
                    <legend>Xử lý môn học</legend>
                    <label className={action === 'USE_EXISTING' ? 'review-choice active' : 'review-choice'}>
                      <input
                        type="radio"
                        name="subject-resolution"
                        checked={action === 'USE_EXISTING'}
                        onChange={() => setChosenAction('USE_EXISTING')}
                      />
                      Gắn vào môn có sẵn
                    </label>
                    <label className={action === 'CREATE' ? 'review-choice active' : 'review-choice'}>
                      <input
                        type="radio"
                        name="subject-resolution"
                        checked={action === 'CREATE'}
                        onChange={() => setChosenAction('CREATE')}
                      />
                      Tạo môn mới
                    </label>
                  </fieldset>
                  {action === 'USE_EXISTING' ? (
                    <div className="admin-stack-gap">
                      <span className="review-label-strong">
                        Chọn môn <span className="admin-required">*</span>
                      </span>
                      {suggestion && <span className="review-suggestion">Gợi ý: {suggestion.name}</span>}
                      <div className="admin-chips" role="group" aria-label="Danh mục môn học">
                        {(subjects ?? [])
                          .filter((subject) => subject.status === 'ACTIVE')
                          .map((subject) => (
                            <button
                              key={subject.id}
                              type="button"
                              aria-pressed={subject.id === subjectId}
                              className={subject.id === subjectId ? 'active' : undefined}
                              onClick={() => setChosenSubjectId(subject.id)}
                            >
                              {subject.name}
                            </button>
                          ))}
                      </div>
                    </div>
                  ) : (
                    <div className="review-new-subject">
                      <span className="review-label">Môn sẽ được tạo</span>
                      <strong>{subjectName.trim() || item.customSubject}</strong>
                    </div>
                  )}
                </>
              ) : (
                <div className="admin-alert admin-alert-success">Môn “{item.subjectName}” đã có trong danh mục.</div>
              )}
              <div className="admin-button-row review-actions">
                <button
                  className="admin-button danger-outline"
                  type="button"
                  disabled={submitting}
                  onClick={() => {
                    setStep('reject');
                    setError('');
                  }}
                >
                  Từ chối
                </button>
                <button
                  className="admin-button"
                  type="button"
                  disabled={submitting || (needsResolution && action === 'USE_EXISTING' && !subjectId)}
                  onClick={approve}
                >
                  {needsResolution && action === 'CREATE'
                    ? 'Tiếp tục tạo môn'
                    : submitting
                      ? 'Đang duyệt…'
                      : 'Duyệt và mở bài đăng'}
                </button>
              </div>
            </>
          )}

          {pending && step === 'reject' && (
            <>
              <ReasonField
                id="request-rejection-reason"
                label="Lý do từ chối"
                value={reason}
                minLength={1}
                maxLength={500}
                showError={showReasonError}
                placeholder="Ví dụ: Nội dung không phải nhu cầu học tập."
                onChange={setReason}
              />
              <div className="admin-alert admin-alert-muted">
                Phí đăng bài sẽ được hoàn tự động vào ví của {kind === 'teaching' ? 'gia sư' : 'học sinh'}.
              </div>
              <div className="admin-button-row review-actions">
                <button
                  className="admin-button secondary"
                  type="button"
                  disabled={submitting}
                  onClick={() => {
                    setStep('resolve');
                    setError('');
                  }}
                >
                  Quay lại
                </button>
                <button className="admin-button danger" type="button" disabled={submitting} onClick={reject}>
                  {submitting ? 'Đang từ chối…' : 'Xác nhận từ chối'}
                </button>
              </div>
            </>
          )}

          {item.status === 'OPEN' && (
            <DefinitionGrid
              columns={2}
              items={[
                ['Môn học', item.subjectName ?? '—'],
                ...(item.originalSubject && item.originalSubject !== item.subjectName
                  ? ([['Môn tự nhập ban đầu', item.originalSubject]] as [string, string][])
                  : []),
                ['Người duyệt', item.reviewer?.name ?? '—'],
                ['Thời gian', formatDateTime(item.reviewedAt)]
              ]}
            />
          )}

          {item.status === 'REJECTED' && (
            <>
              <div className="admin-alert admin-alert-danger">
                <strong>Lý do từ chối</strong>
                <span>{item.rejectionReason || '—'}</span>
              </div>
              <DefinitionGrid
                items={[
                  ['Người từ chối', item.reviewer?.name ?? '—'],
                  ['Thời gian', formatDateTime(item.reviewedAt)]
                ]}
              />
            </>
          )}
        </aside>
      </div>
    </AdminModal>
  );
}

function ReviewText({ label, value }: { label: string; value: string }) {
  return (
    <div className="admin-stack-gap review-text">
      <span className="review-label">{label}</span>
      <p className="admin-prose">{value}</p>
    </div>
  );
}

interface CreateSubjectStepProps {
  item: ReviewItem;
  subjects: Subject[];
  name: string;
  description: string;
  submitting: boolean;
  error: string;
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onBack: () => void;
  onSubmit: () => void;
}

function CreateSubjectStep({
  item,
  subjects,
  name,
  description,
  submitting,
  error,
  onNameChange,
  onDescriptionChange,
  onBack,
  onSubmit
}: CreateSubjectStepProps) {
  const normalized = normalizeName(name);
  const duplicate = subjects.find((subject) => normalizeName(subject.name) === normalized);
  const similar = normalized
    ? subjects.filter((subject) => {
        const other = normalizeName(subject.name);
        return other !== normalized && (other.includes(normalized) || normalized.includes(other));
      })
    : [];
  const blocked = !normalized || Boolean(duplicate);

  const check = !normalized
    ? { tone: 'danger', text: 'Nhập tên môn.' }
    : duplicate
      ? {
          tone: 'danger',
          text:
            duplicate.status === 'ACTIVE'
              ? `Môn “${duplicate.name}” đã có trong danh mục.`
              : `Môn “${duplicate.name}” đã có (đang ngừng dùng).`
        }
      : similar.length
        ? { tone: 'warning', text: `Có môn gần giống: ${similar.map((subject) => `“${subject.name}”`).join(', ')}` }
        : { tone: 'success', text: 'Không trùng tên.' };

  const loadOthers = useCallback(async (signal: AbortSignal) => {
    const query = { status: 'PENDING_REVIEW' as const, page: 0, size: 100 };
    const [teaching, studying] = await Promise.all([
      getAdminTeachingRequests(query, signal),
      getAdminStudyingRequests(query, signal)
    ]);
    return { teaching: teaching.content, studying: studying.content };
  }, []);
  const { data: pendingRequests } = useResource(loadOthers);
  const catalogueNames = new Set(subjects.map((subject) => normalizeName(subject.name)));
  const others = normalized
    ? [
        // Teaching summaries carry the custom name in subjectName; a catalogue name there is not custom.
        ...(pendingRequests?.teaching ?? [])
          .filter(
            (request) =>
              !(item.kind === 'teaching' && request.id === item.id) &&
              normalizeName(request.subjectName) === normalized &&
              !catalogueNames.has(normalized)
          )
          .map((request) => ({
            id: request.id,
            kind: 'teaching' as const,
            title: request.title,
            person: request.tutorName
          })),
        ...(pendingRequests?.studying ?? [])
          .filter(
            (request) =>
              !(item.kind === 'studying' && request.id === item.id) &&
              !request.subjectName &&
              normalizeName(request.note) === normalized
          )
          .map((request) => ({
            id: request.id,
            kind: 'studying' as const,
            title: request.title,
            person: request.studentName
          }))
      ]
    : [];

  return (
    <>
      <div className="admin-modal-pad admin-stack-gap">
        {error && (
          <div className="admin-alert admin-alert-danger" role="alert">
            {error}
          </div>
        )}
        <div className="admin-field">
          <label htmlFor="new-subject-name">
            Tên môn <span className="admin-required">*</span>
          </label>
          <input
            id="new-subject-name"
            className="review-input"
            type="text"
            maxLength={100}
            value={name}
            aria-invalid={blocked}
            aria-describedby="new-subject-check"
            onChange={(event) => onNameChange(event.target.value)}
          />
          <div className="admin-field-help">
            <span />
            <span>{name.length}/100</span>
          </div>
        </div>
        <div className="admin-field">
          <label htmlFor="new-subject-description">Mô tả (không bắt buộc)</label>
          <textarea
            id="new-subject-description"
            rows={3}
            maxLength={500}
            value={description}
            onChange={(event) => onDescriptionChange(event.target.value)}
          />
          <div className="admin-field-help">
            <span />
            <span>{description.length}/500</span>
          </div>
        </div>
        <div className="admin-stack-gap review-check">
          <span className="review-label-strong">Kiểm tra trùng lặp</span>
          <div
            id="new-subject-check"
            className={`admin-alert admin-alert-${check.tone}`}
            role={blocked && normalized ? 'alert' : 'status'}
          >
            {check.text}
          </div>
        </div>
        {others.length > 0 && (
          <div className="admin-stack-gap review-check">
            <span className="review-label-strong">Yêu cầu khác đang chờ cùng môn này</span>
            {others.map((other) => (
              <div className="review-other" key={`${other.kind}-${other.id}`}>
                <span className="admin-badge admin-badge-brand">{reviewKindLabels[other.kind]}</span>
                <div className="admin-stack">
                  <strong>{other.title}</strong>
                  <span>{other.person}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
      <footer className="admin-modal-footer">
        <button className="admin-button secondary" type="button" disabled={submitting} onClick={onBack}>
          Quay lại
        </button>
        <button className="admin-button" type="button" disabled={submitting || blocked} onClick={onSubmit}>
          {submitting ? 'Đang tạo…' : 'Tạo môn và duyệt bài đăng'}
        </button>
      </footer>
    </>
  );
}
