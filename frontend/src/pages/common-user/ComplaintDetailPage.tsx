import { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { getContract, type Contract } from '../../services/studentApi';
import {
  cancelComplaint,
  getComplaintAgainstMe,
  getMyComplaint,
  submitComplaintEvidence,
  updateComplaint,
  respondToComplaint,
  type ComplaintEvidenceInput,
  type ComplaintUpdateInput,
  type StudentComplaint
} from '../../services/studentComplaintApi';
import { ComplaintDetailPanel } from '../../components/common-user/complaint/ComplaintDetailPanel';
import { ComplaintForm, type StudentComplaintFormValues } from '../../components/common-user/complaint/ComplaintForm';
import { ComplaintEvidenceField } from '../../components/common-user/complaint/ComplaintEvidenceField';
import { ComplaintResponseForm } from '../../components/common-user/complaint/ComplaintResponseForm';
import {
  complaintErrorMessage,
  complaintFaultPartyLabels
} from '../../components/common-user/complaint/complaintFormat';
import { ComplaintStatusBadge } from '../../components/common-user/complaint/ComplaintStatusBadge';
import { formatComplaintDate } from '../../components/common-user/complaint/complaintFormat';
import { Icon } from '../../components/ui/Icon';

type Mode = 'submitted' | 'received';

export function ComplaintDetailPage({
  mode = 'submitted',
  basePath = '/student/complaints',
  role = 'student'
}: {
  mode?: Mode;
  basePath?: string;
  role?: 'student' | 'tutor';
}) {
  const { complaintId = '' } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState((location.state as { notice?: string } | null)?.notice ?? '');
  const [extraEvidence, setExtraEvidence] = useState<ComplaintEvidenceInput[]>([]);
  const [evidenceError, setEvidenceError] = useState('');
  const [responseError, setResponseError] = useState('');
  const [evidenceOpen, setEvidenceOpen] = useState(false);

  const load = useCallback(
    async (signal: AbortSignal) => {
      const complaint =
        mode === 'submitted'
          ? await getMyComplaint(complaintId, signal)
          : await getComplaintAgainstMe(complaintId, signal);
      let contract: Contract | null = null;
      try {
        contract = await getContract(complaint.contractId);
      } catch (contractError) {
        if (signal.aborted) throw contractError;
      }
      return { complaint, contract };
    },
    [complaintId, mode]
  );
  const query = useResource(load, revision);
  const complaint: StudentComplaint | null = query.data?.complaint ?? null;
  const contract = query.data?.contract ?? null;
  const editMode = mode === 'submitted' && complaint?.status === 'PENDING' && searchParams.get('edit') === '1';

  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null });
  }, [location.pathname, location.state, navigate]);

  const initialEditValues = useMemo<StudentComplaintFormValues | null>(() => {
    if (!complaint) return null;
    return {
      contractId: complaint.contractId,
      category: complaint.category,
      title: complaint.title,
      content: complaint.content,
      evidences: complaint.evidences.map((evidence) => ({ fileUrl: evidence.fileUrl, fileType: evidence.fileType }))
    };
  }, [complaint]);

  const refresh = () => {
    setRevision((value) => value + 1);
    setExtraEvidence([]);
    setEvidenceError('');
    setResponseError('');
    setEvidenceOpen(false);
  };

  const update = async (values: StudentComplaintFormValues | ComplaintUpdateInput) => {
    if (!complaint) return;
    setBusy(true);
    setError('');
    try {
      await updateComplaint(complaint.id, values as ComplaintUpdateInput);
      setSearchParams({}, { replace: true });
      setNotice('Đã cập nhật khiếu nại.');
      refresh();
    } catch (caught) {
      setError(complaintErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!complaint || !window.confirm('Bạn có chắc muốn hủy khiếu nại này không?')) return;
    setBusy(true);
    setError('');
    try {
      await cancelComplaint(complaint.id);
      setNotice('Đã hủy khiếu nại.');
      refresh();
    } catch (caught) {
      setError(complaintErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const submitEvidence = async () => {
    if (!complaint || extraEvidence.length === 0) {
      setEvidenceError('Vui lòng thêm ít nhất một minh chứng.');
      return;
    }
    setBusy(true);
    setEvidenceError('');
    try {
      await submitComplaintEvidence(complaint.id, extraEvidence);
      setNotice('Đã gửi bổ sung bằng chứng.');
      refresh();
    } catch (caught) {
      setEvidenceError(complaintErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const respond = async (response: string, evidences: ComplaintEvidenceInput[]) => {
    if (!complaint) return;
    setBusy(true);
    setResponseError('');
    try {
      await respondToComplaint(complaint.id, { response, evidences });
      setNotice('Đã gửi phản hồi khiếu nại.');
      refresh();
    } catch (caught) {
      setResponseError(complaintErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const backPath = mode === 'submitted' ? basePath : `${basePath}/received`;

  if (query.error && !complaint)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!complaint || !initialEditValues)
    return <div className="student-complaint-state">Đang tải chi tiết khiếu nại…</div>;

  const contractCode = `HD-${complaint.contractId.slice(0, 8).toUpperCase()}`;
  const respondentName = contract
    ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || contract.tutorEmail
    : 'Chưa cập nhật';

  return (
    <div className="student-complaint-detail-page">
      <button className="student-complaint-back" type="button" onClick={() => navigate(backPath)}>
        <span aria-hidden="true">←</span> Quay lại danh sách khiếu nại
      </button>
      {notice && (
        <div className="student-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}

      {editMode ? (
        <div className="student-complaint-edit-page">
          <header className="student-complaint-edit-header">
            <div className="student-complaint-edit-title">
              <div className="student-complaint-edit-title-icon">
                <Icon name="edit" size={25} />
              </div>
              <div>
                <h1>Chỉnh sửa khiếu nại</h1>
                <p>
                  Cập nhật thông tin khiếu nại của bạn. Chỉ có thể chỉnh sửa khi khiếu nại đang ở trạng thái Chờ tiếp
                  nhận.
                </p>
              </div>
            </div>
          </header>

          <section className="student-complaint-edit-status">
            <span className="student-complaint-edit-status-icon">
              <Icon name="bell" size={21} />
            </span>
            <div>
              <strong>
                Trạng thái hiện tại: <ComplaintStatusBadge status={complaint.status} />
              </strong>
              <p>
                Khiếu nại của bạn vừa được tạo và đang chờ Admin tiếp nhận. Bạn có thể chỉnh sửa nội dung hoặc hủy khiếu
                nại.
              </p>
            </div>
          </section>

          <div className="student-complaint-edit-layout">
            <section className="student-panel student-complaint-edit-card">
              <h2 className="student-complaint-card-title">
                <Icon name="complaint" size={19} /> Thông tin khiếu nại
              </h2>
              <ComplaintForm
                mode="edit"
                formId="student-complaint-edit-form"
                hideActions
                contracts={contract ? [contract] : []}
                initialValues={initialEditValues}
                submitting={busy}
                error={error}
                onSubmit={(values) => void update(values)}
                onCancel={() => setSearchParams({}, { replace: true })}
              />
            </section>

            <aside className="student-complaint-edit-side">
              <section className="student-panel student-complaint-edit-summary">
                <h2 className="student-complaint-card-title">
                  <Icon name="contract" size={18} /> Thông tin tóm tắt
                </h2>
                <div className="student-complaint-edit-summary-rows">
                  <div>
                    <span>
                      <Icon name="calendar" size={15} /> Ngày tạo
                    </span>
                    <strong>{formatComplaintDate(complaint.createdAt)}</strong>
                  </div>
                  <div>
                    <span>
                      <Icon name="contract" size={15} /> Mã hợp đồng
                    </span>
                    <strong>{contractCode}</strong>
                  </div>
                  <div>
                    <span>
                      <Icon name="user" size={15} /> Đối tượng bị khiếu nại
                    </span>
                    <strong>{respondentName} (Gia sư)</strong>
                  </div>
                  <div>
                    <span>
                      <Icon name="bell" size={15} /> Trạng thái
                    </span>
                    <strong>
                      <ComplaintStatusBadge status={complaint.status} />
                    </strong>
                  </div>
                </div>
              </section>

              <section className="student-complaint-edit-note">
                <h2>
                  <Icon name="bell" size={17} /> Lưu ý
                </h2>
                <ul>
                  <li>Bạn chỉ có thể chỉnh sửa nội dung khi khiếu nại là Chờ tiếp nhận.</li>
                  <li>Nếu vấn đề đã được giải quyết hoặc bạn không muốn tiếp tục, bạn có thể hủy khiếu nại.</li>
                  <li>Sau khi lưu thay đổi, thông tin khiếu nại sẽ được cập nhật lại cho Admin.</li>
                </ul>
              </section>
            </aside>
          </div>

          <div className="student-complaint-edit-footer">
            <button
              className="student-button danger outline"
              type="button"
              disabled={busy}
              onClick={() => void cancel()}
            >
              <Icon name="close" size={15} /> Hủy khiếu nại
            </button>
            <div>
              <button
                className="student-button secondary"
                type="button"
                disabled={busy}
                onClick={() => setSearchParams({}, { replace: true })}
              >
                Hủy chỉnh sửa
              </button>
              <button className="student-button" type="submit" form="student-complaint-edit-form" disabled={busy}>
                <Icon name="check" size={15} /> {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
              </button>
            </div>
          </div>
        </div>
      ) : (
        <>
          <header className="student-complaint-detail-header">
            <div>
              <h1>Chi tiết khiếu nại</h1>
              <p>
                Tạo ngày {formatComplaintDate(complaint.createdAt)} <b>·</b> Cập nhật lần cuối{' '}
                {formatComplaintDate(complaint.updatedAt)}
              </p>
            </div>
            {mode === 'submitted' && complaint.status === 'PENDING' && (
              <div className="student-complaint-detail-header-actions">
                <button
                  className="student-button"
                  type="button"
                  disabled={busy}
                  onClick={() => setSearchParams({ edit: '1' })}
                >
                  <Icon name="edit" size={15} /> Chỉnh sửa
                </button>
                <button
                  className="student-button secondary"
                  type="button"
                  disabled={busy}
                  onClick={() => void cancel()}
                >
                  <Icon name="close" size={15} /> Hủy khiếu nại
                </button>
              </div>
            )}
          </header>
          <div className="student-complaint-detail-layout">
            <main>
              <ComplaintDetailPanel
                complaint={complaint}
                contract={contract}
                mode={mode}
                role={role}
                contractHref={`${basePath.startsWith('/tutor') ? '/tutor/sections/contracts' : '/student/contracts'}/${complaint.contractId}`}
              />
              {mode === 'received' &&
                (complaint.status === 'IN_REVIEW' || complaint.status === 'AWAITING_EVIDENCE') &&
                !complaint.respondentRespondedAt && (
                  <ComplaintResponseForm
                    busy={busy}
                    error={responseError}
                    onSubmit={(response, evidences) => void respond(response, evidences)}
                  />
                )}
              {mode === 'received' && complaint.respondentRespondedAt && (
                <div className="student-notice student-complaint-response-notice" role="status">
                  Bạn đã gửi phản hồi cho khiếu nại này.
                </div>
              )}
            </main>
            <aside className="student-complaint-detail-side">
              <ComplaintCurrentStatus complaint={complaint} />
              <ComplaintProgress complaint={complaint} />
              <ComplaintResult complaint={complaint} mode={mode} />
              <section className="student-complaint-note">
                <h2>
                  <Icon name="bell" size={17} /> Lưu ý
                </h2>
                <ul>
                  <li>Bạn có thể gửi thêm minh chứng trong thời gian khiếu nại đang được xử lý.</li>
                  <li>Sau khi có kết quả, bạn sẽ nhận được thông báo qua hệ thống.</li>
                </ul>
              </section>
              {mode === 'submitted' && complaint.status === 'AWAITING_EVIDENCE' && (
                <section className="student-complaint-action-panel student-complaint-evidence-action">
                  {!evidenceOpen ? (
                    <button
                      className="student-button"
                      type="button"
                      disabled={busy}
                      onClick={() => setEvidenceOpen(true)}
                    >
                      <Icon name="send" size={15} /> Gửi thêm minh chứng
                    </button>
                  ) : (
                    <>
                      <h2>Gửi thêm minh chứng</h2>
                      <p>Vui lòng bổ sung các tài liệu được yêu cầu.</p>
                      {evidenceError && (
                        <div className="student-error" role="alert">
                          {evidenceError}
                        </div>
                      )}
                      <ComplaintEvidenceField value={extraEvidence} onChange={setExtraEvidence} />
                      <div className="student-form-actions">
                        <button
                          className="student-button"
                          type="button"
                          disabled={busy}
                          onClick={() => void submitEvidence()}
                        >
                          Gửi bằng chứng
                        </button>
                      </div>
                    </>
                  )}
                </section>
              )}
            </aside>
          </div>
        </>
      )}
    </div>
  );
}

function ComplaintCurrentStatus({ complaint }: { complaint: StudentComplaint }) {
  const active = complaint.status === 'IN_REVIEW' || complaint.status === 'AWAITING_EVIDENCE';
  const final = complaint.status === 'RESOLVED' || complaint.status === 'REJECTED';
  const message =
    complaint.status === 'PENDING'
      ? 'Khiếu nại đang chờ Admin tiếp nhận và xem xét.'
      : final
        ? 'Khiếu nại đã có kết quả xử lý từ Admin.'
        : active
          ? 'Admin đang xem xét và xử lý khiếu nại của bạn.'
          : 'Khiếu nại đã được cập nhật.';

  return (
    <section className="student-panel student-complaint-current-status">
      <h2 className="student-complaint-card-title">
        <Icon name="bell" size={18} /> Trạng thái hiện tại
      </h2>
      <div className="student-complaint-current-status-badge">
        <ComplaintStatusBadge status={complaint.status} />
      </div>
      <p>{message}</p>
      {active && <small>Vui lòng theo dõi phản hồi từ Admin bên dưới.</small>}
    </section>
  );
}

function ComplaintResult({ complaint, mode }: { complaint: StudentComplaint; mode: Mode }) {
  const final = complaint.status === 'RESOLVED' || complaint.status === 'REJECTED';
  const canSeeResolution = mode === 'submitted' || final;
  return (
    <section className={`student-panel student-complaint-side-result ${final ? complaint.status.toLowerCase() : ''}`}>
      <h2 className="student-complaint-card-title">
        <Icon name="award" size={18} /> Kết quả xử lý
      </h2>
      <div className="student-complaint-result-highlight">
        <Icon name={final ? 'check' : 'bell'} size={16} />
        <strong>{canSeeResolution && complaint.resolution ? complaint.resolution : 'Chưa có kết quả'}</strong>
      </div>
      <div className="student-complaint-result-party">
        <span>
          <Icon name="user" size={15} /> Bên chịu trách nhiệm
        </span>
        <strong>{final && complaint.faultParty ? complaintFaultPartyLabels[complaint.faultParty] : '-'}</strong>
      </div>
    </section>
  );
}

function ComplaintProgress({ complaint }: { complaint: StudentComplaint }) {
  const reviewing = complaint.status === 'IN_REVIEW' || complaint.status === 'AWAITING_EVIDENCE';
  const final = complaint.status === 'RESOLVED' || complaint.status === 'REJECTED';
  const pending = complaint.status === 'PENDING';
  return (
    <section className="student-panel student-complaint-progress">
      <h2 className="student-complaint-card-title">
        <Icon name="calendar" size={18} /> Tiến trình xử lý
      </h2>
      <ol>
        <li className="done">
          <span className="student-complaint-step-dot">✓</span>
          <div>
            <strong>Đã gửi khiếu nại</strong>
            <small>{formatComplaintDate(complaint.createdAt)}</small>
            <p>Bạn đã gửi khiếu nại tới hệ thống.</p>
          </div>
        </li>
        <li className={reviewing ? 'current done' : pending ? 'current' : final ? 'done' : ''}>
          <span className="student-complaint-step-dot">{pending ? '2' : '✓'}</span>
          <div>
            <strong>{pending ? 'Chờ tiếp nhận' : 'Đang xử lý'}</strong>
            <small>{reviewing || final ? formatComplaintDate(complaint.updatedAt) : '—'}</small>
            <p>
              {pending
                ? 'Khiếu nại sẽ được Admin tiếp nhận và xem xét.'
                : 'Quản trị viên đang xem xét và xử lý khiếu nại.'}
            </p>
          </div>
        </li>
        <li className={final ? 'current done' : ''}>
          <span className="student-complaint-step-dot">{final ? '✓' : '3'}</span>
          <div>
            <strong>{final ? 'Đã có kết quả' : 'Chưa có kết quả'}</strong>
            <small>{final ? formatComplaintDate(complaint.resolvedAt) : '—'}</small>
            <p>{final ? 'Hệ thống đã cập nhật kết quả xử lý.' : 'Hệ thống sẽ thông báo khi có kết quả.'}</p>
          </div>
        </li>
      </ol>
    </section>
  );
}
