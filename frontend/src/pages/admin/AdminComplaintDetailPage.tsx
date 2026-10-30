import { useCallback, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useResource } from '../../hooks/useResource';
import {
  acceptComplaint,
  getAdminComplaint,
  getComplaintHandlers,
  reassignComplaint,
  rejectComplaint,
  requestComplaintEvidence,
  startComplaintReview,
  type AdminComplaintDetail,
  type ComplaintFaultParty
} from '../../services/complaintApi';
import { evidenceName, formatClockTime, formatLocalDateTime, formatMoney } from '../../components/refund/refundFormat';
import {
  contractStatusMeta,
  formatDate,
  formatTime,
  fullName,
  lessonStatusMeta,
  paymentPeriodLabels,
  teachingModeLabels
} from './adminFormat';
import { AdminModal, AdminState, StatusBadge } from './components/AdminUi';
import {
  complaintCategoryLabels,
  complaintErrorMessage,
  complaintStatusMeta,
  faultPartyLabels,
  installmentStatusMeta,
  partyRoleLabels,
  paymentMethodLabels,
  paymentStatusMeta,
  reviewerName
} from './complaintFormat';

type Dialog = 'accept' | 'reject' | 'evidence' | 'reassign';
type Tab = 'lessons' | 'installments' | 'payments';

const RESOLUTION_MAX = 5000;
const faultParties = Object.keys(faultPartyLabels) as ComplaintFaultParty[];

export function AdminComplaintDetailPage() {
  const { complaintId = '' } = useParams();
  const { user } = useAuth();
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [tab, setTab] = useState<Tab>('lessons');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  const load = useCallback((signal: AbortSignal) => getAdminComplaint(complaintId, signal), [complaintId]);
  const { data, error, isLoading } = useResource(load, revision);

  if (!data) {
    return (
      <div className="admin-page">
        <AdminState loading={isLoading} error={error} />
      </div>
    );
  }

  const isAdmin = user?.role === 'ADMIN';
  const mine = data.reviewer?.userId === user?.userId;
  const claimable = data.status === 'PENDING' && !data.reviewer;
  const deciding = data.status === 'IN_REVIEW' && mine;
  const reassignable =
    isAdmin && data.reviewer !== null && (data.status === 'IN_REVIEW' || data.status === 'AWAITING_EVIDENCE');
  const closed = data.status === 'RESOLVED' || data.status === 'REJECTED';

  const run = async (action: () => Promise<unknown>, success: string) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      setDialog(null);
      setMessage({ tone: 'success', text: success });
    } catch (actionError) {
      setDialog(null);
      setMessage({ tone: 'danger', text: complaintErrorMessage(actionError) });
    } finally {
      setBusy(false);
      setRevision((value) => value + 1);
    }
  };

  const uploaderLabel = (userId: string | null) =>
    userId === data.complainant.userId
      ? partyRoleLabels[data.complainant.role]
      : userId === data.respondent.userId
        ? partyRoleLabels[data.respondent.role]
        : 'Khác';

  return (
    <div className="admin-page">
      <Link className="admin-back" to="/admin/complaints">
        ‹ Danh sách khiếu nại
      </Link>
      <header className="admin-complaint-header">
        <div className="admin-stack-gap">
          <div className="admin-tags">
            <StatusBadge meta={complaintStatusMeta[data.status]} />
            <span className="admin-badge admin-badge-neutral">{complaintCategoryLabels[data.category]}</span>
          </div>
          <h1 className="admin-title">{data.title}</h1>
          <span className="admin-complaint-meta">
            Gửi {formatLocalDateTime(data.createdAt)} · Cập nhật {formatLocalDateTime(data.updatedAt)}
          </span>
        </div>
        <div className="admin-button-row">
          {claimable && (
            <button
              className="admin-button"
              type="button"
              disabled={busy}
              onClick={() => void run(() => startComplaintReview(data.id), 'Đã tiếp nhận khiếu nại.')}
            >
              Tiếp nhận xử lý
            </button>
          )}
          {reassignable && (
            <button className="admin-button secondary" type="button" onClick={() => setDialog('reassign')}>
              Chuyển người xử lý
            </button>
          )}
          {deciding && (
            <>
              <button className="admin-button secondary" type="button" onClick={() => setDialog('evidence')}>
                Yêu cầu bằng chứng
              </button>
              <button className="admin-button danger-outline" type="button" onClick={() => setDialog('reject')}>
                Từ chối
              </button>
              <button className="admin-button" type="button" onClick={() => setDialog('accept')}>
                Chấp nhận
              </button>
            </>
          )}
        </div>
      </header>

      {message && (
        <div
          className={`admin-alert admin-alert-${message.tone}`}
          role={message.tone === 'danger' ? 'alert' : 'status'}
        >
          {message.text}
        </div>
      )}

      {data.status === 'AWAITING_EVIDENCE' && data.resolution && (
        <div className="admin-alert admin-alert-muted">
          <strong>Đã yêu cầu bổ sung bằng chứng</strong>
          <p className="admin-prose">{data.resolution}</p>
        </div>
      )}

      {closed && (
        <section className="admin-card admin-card-pad">
          <div className="admin-section-heading">
            <h2>Kết luận</h2>
            <span>
              {formatClockTime(data.resolvedAt)}
              {data.reviewer && ` · ${reviewerName(data.reviewer.name)}`}
            </span>
          </div>
          {data.faultParty && (
            <div className="admin-key-values">
              <div>
                <span>Bên có lỗi</span>
                <strong>{faultPartyLabels[data.faultParty]}</strong>
              </div>
            </div>
          )}
          <p className="admin-prose">{data.resolution}</p>
        </section>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          <section className="admin-card admin-card-pad">
            <h2>Nội dung</h2>
            <p className="admin-prose">{data.content}</p>
          </section>

          <section className="admin-card admin-card-pad">
            <h2>Bằng chứng ({data.evidences.length})</h2>
            {data.evidences.length === 0 ? (
              <span className="admin-state">Chưa có bằng chứng.</span>
            ) : (
              <ul className="admin-evidence-list">
                {data.evidences.map((evidence) => (
                  <li key={evidence.id}>
                    <a className="admin-link" href={evidence.fileUrl} target="_blank" rel="noreferrer">
                      {evidenceName(evidence)}
                    </a>
                    <span className="admin-badge admin-badge-neutral">{uploaderLabel(evidence.uploadedByUserId)}</span>
                    <span>{formatLocalDateTime(evidence.createdAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Phản hồi của {partyRoleLabels[data.respondent.role].toLowerCase()}</h2>
              {data.respondent.respondedAt && <span>{formatClockTime(data.respondent.respondedAt)}</span>}
            </div>
            {data.respondent.response ? (
              <p className="admin-prose">{data.respondent.response}</p>
            ) : (
              <span className="admin-state">Chưa có phản hồi.</span>
            )}
          </section>

          <ContractRecords data={data} tab={tab} onTabChange={setTab} />
        </div>

        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Các bên</h2>
            <Party label="Người khiếu nại" party={data.complainant} />
            <Party label="Bên bị khiếu nại" party={data.respondent} />
          </section>

          <section className="admin-card admin-card-pad">
            <h2>Người xử lý</h2>
            {data.reviewer ? (
              <div className="admin-stack">
                <strong>{reviewerName(data.reviewer.name)}</strong>
                {mine && <span>Bạn</span>}
              </div>
            ) : (
              <span className="admin-state">Chưa có người xử lý.</span>
            )}
          </section>

          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Hợp đồng</h2>
              <StatusBadge meta={contractStatusMeta[data.contract.status]} />
            </div>
            <div className="admin-key-values">
              <div>
                <span>Môn học</span>
                <strong>
                  {[data.contract.subjectName, data.contract.gradeName].filter(Boolean).join(' · ') || '—'}
                </strong>
              </div>
              <div>
                <span>Học viên</span>
                <strong>{data.contract.studentName}</strong>
              </div>
              <div>
                <span>Gia sư</span>
                <strong>{data.contract.tutorName}</strong>
              </div>
              <div>
                <span>Hình thức</span>
                <strong>{teachingModeLabels[data.contract.teachingMode]}</strong>
              </div>
              <div>
                <span>Học phí</span>
                <strong>
                  {formatMoney(data.contract.price)} {paymentPeriodLabels[data.contract.paymentPeriod]}
                </strong>
              </div>
              <div>
                <span>Số buổi</span>
                <strong>{data.contract.totalLessons}</strong>
              </div>
              <div>
                <span>Thời gian</span>
                <strong>
                  {formatDate(data.contract.startDate)} – {formatDate(data.contract.endDate)}
                </strong>
              </div>
            </div>
          </section>
        </aside>
      </div>

      {dialog === 'accept' && (
        <AcceptDialog
          data={data}
          busy={busy}
          onClose={() => setDialog(null)}
          onSubmit={(resolution, faultParty) =>
            void run(() => acceptComplaint(data.id, resolution, faultParty), 'Đã chấp nhận khiếu nại.')
          }
        />
      )}
      {dialog === 'reject' && (
        <TextDialog
          title="Từ chối khiếu nại"
          label="Lý do từ chối"
          submitLabel="Từ chối"
          danger
          busy={busy}
          onClose={() => setDialog(null)}
          onSubmit={(text) => void run(() => rejectComplaint(data.id, text), 'Đã từ chối khiếu nại.')}
        />
      )}
      {dialog === 'evidence' && (
        <TextDialog
          title="Yêu cầu bổ sung bằng chứng"
          label={`Gửi tới ${data.complainant.name}`}
          submitLabel="Gửi yêu cầu"
          busy={busy}
          onClose={() => setDialog(null)}
          onSubmit={(text) =>
            void run(() => requestComplaintEvidence(data.id, text), 'Đã gửi yêu cầu bổ sung bằng chứng.')
          }
        />
      )}
      {dialog === 'reassign' && data.reviewer && (
        <ReassignDialog
          currentEmployeeId={data.reviewer.employeeId}
          busy={busy}
          onClose={() => setDialog(null)}
          onSubmit={(employeeId, name) =>
            void run(() => reassignComplaint(data.id, employeeId), `Đã chuyển khiếu nại cho ${name}.`)
          }
        />
      )}
    </div>
  );
}

function Party({ label, party }: { label: string; party: AdminComplaintDetail['complainant'] }) {
  return (
    <div className="admin-stack">
      <span>{label}</span>
      <div className="admin-tags">
        <strong>{party.name}</strong>
        <span className={`admin-badge admin-badge-${party.role === 'TUTOR' ? 'brand' : 'neutral'}`}>
          {partyRoleLabels[party.role]}
        </span>
      </div>
      <span>{party.email}</span>
    </div>
  );
}

function ContractRecords({
  data,
  tab,
  onTabChange
}: {
  data: AdminComplaintDetail;
  tab: Tab;
  onTabChange: (tab: Tab) => void;
}) {
  const tabs: [Tab, string, number][] = [
    ['lessons', 'Buổi học', data.lessons.length],
    ['installments', 'Kỳ thanh toán', data.installments.length],
    ['payments', 'Thanh toán', data.payments.length]
  ];
  const lessonTitles = new Map(data.lessons.map((lesson, index) => [lesson.id, lesson.title || `Buổi ${index + 1}`]));

  return (
    <section className="admin-card">
      <div className="admin-tabs admin-tabs-inset" role="tablist" aria-label="Dữ liệu hợp đồng">
        {tabs.map(([value, label, count]) => (
          <button
            key={value}
            type="button"
            role="tab"
            aria-selected={tab === value}
            className={tab === value ? 'active' : undefined}
            onClick={() => onTabChange(value)}
          >
            {label} <span className="admin-count">{count}</span>
          </button>
        ))}
      </div>

      {tab === 'lessons' &&
        (data.lessons.length === 0 ? (
          <AdminState empty="Chưa có buổi học." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Buổi</th>
                  <th>Ngày</th>
                  <th>Giờ</th>
                  <th>Hình thức</th>
                  <th>Trạng thái</th>
                  <th>Ghi chú</th>
                </tr>
              </thead>
              <tbody>
                {data.lessons.map((lesson) => (
                  <tr key={lesson.id}>
                    <td>{lessonTitles.get(lesson.id)}</td>
                    <td className="admin-nowrap">{formatDate(lesson.date)}</td>
                    <td className="admin-nowrap">
                      {formatTime(lesson.startTime)} – {formatTime(lesson.endTime)}
                    </td>
                    <td>{teachingModeLabels[lesson.teachingMode]}</td>
                    <td>
                      <StatusBadge meta={lessonStatusMeta[lesson.status]} />
                    </td>
                    <td>{lesson.note || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

      {tab === 'installments' &&
        (data.installments.length === 0 ? (
          <AdminState empty="Chưa có kỳ thanh toán." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Kỳ</th>
                  <th>Buổi</th>
                  <th>Số tiền</th>
                  <th>Hạn</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {data.installments.map((installment) => (
                  <tr key={installment.id}>
                    <td>Kỳ {installment.sequenceNumber}</td>
                    <td>{installment.lessonId ? (lessonTitles.get(installment.lessonId) ?? '—') : '—'}</td>
                    <td className="admin-nowrap">{formatMoney(installment.amount)}</td>
                    <td className="admin-nowrap">{formatDate(installment.dueDate)}</td>
                    <td>
                      <StatusBadge meta={installmentStatusMeta[installment.status]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

      {tab === 'payments' &&
        (data.payments.length === 0 ? (
          <AdminState empty="Chưa có thanh toán." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Mã giao dịch</th>
                  <th>Phương thức</th>
                  <th>Số tiền</th>
                  <th>Thanh toán lúc</th>
                  <th>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {data.payments.map((payment) => (
                  <tr key={payment.id}>
                    <td className="admin-nowrap">{payment.transactionCode ?? '—'}</td>
                    <td>{paymentMethodLabels[payment.paymentMethod]}</td>
                    <td className="admin-nowrap">{formatMoney(payment.amount)}</td>
                    <td className="admin-nowrap">{formatLocalDateTime(payment.paidAt)}</td>
                    <td>
                      <StatusBadge meta={paymentStatusMeta[payment.status]} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}
    </section>
  );
}

function TextField({
  id,
  label,
  value,
  showError,
  onChange
}: {
  id: string;
  label: string;
  value: string;
  showError: boolean;
  onChange: (value: string) => void;
}) {
  const invalid = showError && !value.trim();
  return (
    <div className="admin-field">
      <label htmlFor={id}>
        {label} <span className="admin-required">*</span>
      </label>
      <textarea
        id={id}
        rows={6}
        maxLength={RESOLUTION_MAX}
        value={value}
        aria-invalid={invalid}
        aria-describedby={`${id}-help`}
        onChange={(event) => onChange(event.target.value)}
      />
      <div id={`${id}-help`} className="admin-field-help">
        <span className={invalid ? 'admin-field-error' : undefined}>{invalid ? 'Vui lòng nhập nội dung.' : ''}</span>
        <span>
          {value.length}/{RESOLUTION_MAX}
        </span>
      </div>
    </div>
  );
}

function DialogFooter({
  submitLabel,
  danger,
  busy,
  onClose,
  onSubmit
}: {
  submitLabel: string;
  danger?: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <div className="admin-button-row">
      <button className="admin-button secondary" type="button" onClick={onClose}>
        Huỷ
      </button>
      <button className={`admin-button${danger ? ' danger' : ''}`} type="button" disabled={busy} onClick={onSubmit}>
        {submitLabel}
      </button>
    </div>
  );
}

function TextDialog({
  title,
  label,
  submitLabel,
  danger,
  busy,
  onClose,
  onSubmit
}: {
  title: string;
  label: string;
  submitLabel: string;
  danger?: boolean;
  busy: boolean;
  onClose: () => void;
  onSubmit: (text: string) => void;
}) {
  const [text, setText] = useState('');
  const [tried, setTried] = useState(false);
  return (
    <AdminModal
      title={title}
      size="sm"
      onClose={onClose}
      footer={
        <DialogFooter
          submitLabel={submitLabel}
          danger={danger}
          busy={busy}
          onClose={onClose}
          onSubmit={() => {
            setTried(true);
            if (text.trim()) onSubmit(text.trim());
          }}
        />
      }
    >
      <div className="admin-modal-pad">
        <TextField id="complaint-dialog-text" label={label} value={text} showError={tried} onChange={setText} />
      </div>
    </AdminModal>
  );
}

function AcceptDialog({
  data,
  busy,
  onClose,
  onSubmit
}: {
  data: AdminComplaintDetail;
  busy: boolean;
  onClose: () => void;
  onSubmit: (resolution: string, faultParty: ComplaintFaultParty) => void;
}) {
  const [faultParty, setFaultParty] = useState<ComplaintFaultParty | null>(null);
  const [resolution, setResolution] = useState('');
  const [tried, setTried] = useState(false);
  const partyHint: Partial<Record<ComplaintFaultParty, string>> = {
    RESPONDENT: `${data.respondent.name} · ${partyRoleLabels[data.respondent.role]}`,
    COMPLAINANT: `${data.complainant.name} · ${partyRoleLabels[data.complainant.role]}`
  };

  return (
    <AdminModal
      title="Chấp nhận khiếu nại"
      size="sm"
      onClose={onClose}
      footer={
        <DialogFooter
          submitLabel="Chấp nhận"
          busy={busy}
          onClose={onClose}
          onSubmit={() => {
            setTried(true);
            if (faultParty && resolution.trim()) onSubmit(resolution.trim(), faultParty);
          }}
        />
      }
    >
      <div className="admin-modal-pad admin-stack-gap">
        <fieldset className="admin-choice-group">
          <legend>
            Bên có lỗi <span className="admin-required">*</span>
          </legend>
          <div className="admin-choices">
            {faultParties.map((value) => (
              <label key={value} className={faultParty === value ? 'active' : undefined}>
                <input
                  type="radio"
                  name="fault-party"
                  checked={faultParty === value}
                  onChange={() => setFaultParty(value)}
                />
                <span className="admin-stack">
                  <strong>{faultPartyLabels[value]}</strong>
                  {partyHint[value] && <span>{partyHint[value]}</span>}
                </span>
              </label>
            ))}
          </div>
          {tried && !faultParty && <span className="admin-field-error">Vui lòng chọn bên có lỗi.</span>}
        </fieldset>
        <TextField
          id="complaint-resolution"
          label="Kết luận"
          value={resolution}
          showError={tried}
          onChange={setResolution}
        />
      </div>
    </AdminModal>
  );
}

function ReassignDialog({
  currentEmployeeId,
  busy,
  onClose,
  onSubmit
}: {
  currentEmployeeId: string;
  busy: boolean;
  onClose: () => void;
  onSubmit: (employeeId: string, name: string) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const load = useCallback((signal: AbortSignal) => getComplaintHandlers('', signal), []);
  const { data, error, isLoading } = useResource(load);

  const handlers = useMemo(() => {
    const term = keyword.trim().toLowerCase();
    return (data?.content ?? [])
      .map((handler) => ({ ...handler, name: fullName(handler.firstName, handler.lastName, handler.email) }))
      .filter((handler) => !term || `${handler.name} ${handler.email}`.toLowerCase().includes(term));
  }, [data, keyword]);
  const chosen = handlers.find((handler) => handler.employeeId === selected);

  return (
    <AdminModal
      title="Chuyển người xử lý"
      size="sm"
      onClose={onClose}
      footer={
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" onClick={onClose}>
            Huỷ
          </button>
          <button
            className="admin-button"
            type="button"
            disabled={busy || !chosen}
            onClick={() => chosen && onSubmit(chosen.employeeId, chosen.name)}
          >
            Chuyển
          </button>
        </div>
      }
    >
      <div className="admin-modal-pad admin-stack-gap">
        <div className="admin-field">
          <label htmlFor="complaint-handler-search">Tìm nhân viên</label>
          <input
            id="complaint-handler-search"
            type="search"
            placeholder="Tên hoặc email"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
        </div>
        {!data ? (
          <AdminState loading={isLoading} error={error} />
        ) : handlers.length === 0 ? (
          <AdminState empty="Không có nhân viên phù hợp." />
        ) : (
          <div className="admin-choices admin-choices-list" role="radiogroup" aria-label="Nhân viên xử lý khiếu nại">
            {handlers.map((handler) => {
              const current = handler.employeeId === currentEmployeeId;
              return (
                <label key={handler.employeeId} className={selected === handler.employeeId ? 'active' : undefined}>
                  <input
                    type="radio"
                    name="complaint-handler"
                    disabled={current}
                    checked={selected === handler.employeeId}
                    onChange={() => setSelected(handler.employeeId)}
                  />
                  <span className="admin-stack">
                    <strong>{handler.name}</strong>
                    <span>{current ? `${handler.email} · Hiện tại` : handler.email}</span>
                  </span>
                </label>
              );
            })}
          </div>
        )}
      </div>
    </AdminModal>
  );
}
