import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../../hooks/useResource';
import {
  approveCertificate,
  getAdminCertificate,
  rejectCertificate,
  type AdminCertificateDetail
} from '../../../services/adminTutorApi';
import { certificateStatusMeta, formatDate, formatDateTime, isExpired } from '../adminFormat';
import { AdminModal, AdminState, DefinitionGrid, ReasonField, StatusBadge } from './AdminUi';

const MAX_REASON = 500;

interface Props {
  certificateId: string;
  showTutor?: boolean;
  onClose: () => void;
  onReviewed: (certificate: AdminCertificateDetail) => void;
}

export function CertificateReviewModal({ certificateId, showTutor = false, onClose, onReviewed }: Props) {
  const load = useCallback((signal: AbortSignal) => getAdminCertificate(certificateId, signal), [certificateId]);
  const { data: certificate, error, isLoading } = useResource(load);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState('');

  const submit = async (action: () => Promise<AdminCertificateDetail>) => {
    setSubmitting(true);
    setActionError('');
    try {
      onReviewed(await action());
    } catch (caught) {
      setActionError(caught instanceof Error ? caught.message : 'Không thể xử lý chứng chỉ.');
    } finally {
      setSubmitting(false);
    }
  };

  const confirmReject = () => {
    setTouched(true);
    if (!reason.trim()) return;
    void submit(() => rejectCertificate(certificateId, reason.trim()));
  };

  return (
    <AdminModal
      size="xl"
      title={certificate?.name ?? 'Chứng chỉ'}
      subtitle={
        certificate && (
          <>
            <StatusBadge meta={certificateStatusMeta[certificate.status]} />
            {certificate.issuingOrganization && <span>{certificate.issuingOrganization}</span>}
          </>
        )
      }
      onClose={onClose}
    >
      {!certificate ? (
        <div className="admin-modal-pad">
          <AdminState loading={isLoading} error={error} />
        </div>
      ) : (
        <div className="admin-document-layout">
          <DocumentPreview url={certificate.certificateUrl} name={certificate.name} />
          <div className="admin-document-side">
            {showTutor && (
              <div className="admin-tutor-chip">
                <div>
                  <strong>{certificate.tutorName}</strong>
                  <span>{certificate.tutorEmail}</span>
                </div>
                <Link to={`/admin/tutor-profiles/${certificate.tutorUserId}`}>Xem hồ sơ</Link>
              </div>
            )}
            <h3>Thông tin chứng chỉ</h3>
            <DefinitionGrid
              items={[
                ['Ngày cấp', formatDate(certificate.issueDate)],
                ['Ngày hết hạn', certificate.expiryDate ? formatDate(certificate.expiryDate) : 'Không thời hạn'],
                ['Gửi lúc', formatDateTime(certificate.createdAt)],
                ['Mô tả', certificate.description || '—']
              ]}
            />
            {certificate.status !== 'PENDING' && certificate.reviewedAt && (
              <div
                className={`admin-alert ${certificate.status === 'REJECTED' ? 'admin-alert-danger' : 'admin-alert-muted'}`}
              >
                <strong>
                  Xử lý bởi {certificate.reviewedBy?.name || '—'} · {formatDateTime(certificate.reviewedAt)}
                </strong>
                {certificate.rejectionReason && <span>Lý do: {certificate.rejectionReason}</span>}
              </div>
            )}
            {actionError && (
              <div className="admin-alert admin-alert-danger" role="alert">
                {actionError}
              </div>
            )}
            {certificate.status === 'PENDING' && (
              <div className="admin-document-actions">
                {rejecting ? (
                  <>
                    <ReasonField
                      id="certificate-reason"
                      label="Lý do từ chối chứng chỉ"
                      value={reason}
                      minLength={1}
                      maxLength={MAX_REASON}
                      showError={touched}
                      placeholder="Ví dụ: Ảnh mờ, không đọc được số hiệu chứng chỉ."
                      onChange={setReason}
                    />
                    <div className="admin-button-row">
                      <button className="admin-button secondary" type="button" onClick={() => setRejecting(false)}>
                        Hủy
                      </button>
                      <button
                        className="admin-button danger"
                        type="button"
                        disabled={submitting}
                        onClick={confirmReject}
                      >
                        {submitting ? 'Đang gửi…' : 'Xác nhận từ chối'}
                      </button>
                    </div>
                  </>
                ) : (
                  <>
                    {isExpired(certificate.expiryDate) && (
                      <div className="admin-alert admin-alert-warning">Chứng chỉ đã hết hạn, không thể xác minh.</div>
                    )}
                    <div className="admin-button-row">
                      <button className="admin-button danger-outline" type="button" onClick={() => setRejecting(true)}>
                        Từ chối
                      </button>
                      {!isExpired(certificate.expiryDate) && (
                        <button
                          className="admin-button"
                          type="button"
                          disabled={submitting}
                          onClick={() => void submit(() => approveCertificate(certificateId))}
                        >
                          {submitting ? 'Đang xác minh…' : 'Xác minh chứng chỉ'}
                        </button>
                      )}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </AdminModal>
  );
}

function isPdf(url: string) {
  try {
    return new URL(url).pathname.toLowerCase().endsWith('.pdf');
  } catch {
    return url.toLowerCase().split('?')[0].endsWith('.pdf');
  }
}

function DocumentPreview({ url, name }: { url: string | null; name: string }) {
  const [zoom, setZoom] = useState(100);
  const usable = url && /^https?:\/\//i.test(url) ? url : null;
  const pdf = usable ? isPdf(usable) : false;

  return (
    <div className="admin-document-preview">
      <div className="admin-document-toolbar">
        {!pdf && usable ? (
          <div className="admin-zoom">
            <button type="button" aria-label="Thu nhỏ" disabled={zoom <= 50} onClick={() => setZoom(zoom - 25)}>
              −
            </button>
            <span>{zoom}%</span>
            <button type="button" aria-label="Phóng to" disabled={zoom >= 300} onClick={() => setZoom(zoom + 25)}>
              +
            </button>
          </div>
        ) : (
          <span />
        )}
        {usable && (
          <a href={usable} target="_blank" rel="noreferrer">
            Mở tệp gốc
          </a>
        )}
      </div>
      <div className="admin-document-canvas">
        {!usable ? (
          <div className="admin-state">Không có tệp đính kèm.</div>
        ) : pdf ? (
          <iframe title={name} src={usable} />
        ) : (
          <img src={usable} alt={name} style={{ width: `${zoom}%` }} />
        )}
      </div>
    </div>
  );
}
