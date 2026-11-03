import { useState, type FormEvent } from 'react';
import { ApiError } from '../../services/httpClient';
import {
  createTutorCertificate,
  deleteTutorCertificate,
  getTutorCertificate,
  updateTutorCertificate,
  uploadCertificateFile,
  type TutorCertificate
} from '../../services/tutorApi';
import { useTutorCertificates } from '../../hooks/useTutorWorkspace';
import { tutorStatusLabel } from '../../utils/tutorDisplay';

const statuses = ['ALL', 'PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'] as const;
type CertificateFilter = (typeof statuses)[number];

export function TutorCertificatesPage() {
  const [filter, setFilter] = useState<CertificateFilter>('ALL');
  const [revision, setRevision] = useState(0);
  const certificateQuery = useTutorCertificates(filter === 'ALL' ? undefined : filter, revision);
  const certificates = certificateQuery.data ?? [];
  const [editing, setEditing] = useState<TutorCertificate | null>(null);
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const loading = certificateQuery.isLoading;

  const saveCertificate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const file = values.get('file');
    const selectedFile = file instanceof File && file.size > 0 ? file : null;
    const text = (name: string) => String(values.get(name) ?? '').trim();
    setSaving(true);
    setError('');
    setNotice('');
    try {
      let uploadedReference: string | undefined;
      if (selectedFile) uploadedReference = (await uploadCertificateFile(selectedFile)).certificateUrl;
      const payload = {
        name: text('name'),
        issuingOrganization: text('issuingOrganization') || undefined,
        description: text('description') || undefined,
        issueDate: text('issueDate') || undefined,
        expiryDate: text('expiryDate') || undefined,
        ...(uploadedReference ? { certificateUrl: uploadedReference } : {})
      };
      if (!editing && !uploadedReference && !text('certificateUrl')) {
        setError('Vui lòng tải tài liệu lên hoặc nhập đường dẫn chứng chỉ.');
        return;
      }
      if (editing) {
        await updateTutorCertificate(editing.id, {
          ...payload,
          ...(!uploadedReference && text('certificateUrl') ? { certificateUrl: text('certificateUrl') } : {}),
          certificateUrl: uploadedReference || text('certificateUrl') || undefined
        });
        setNotice('Đã cập nhật chứng chỉ và gửi để xét duyệt.');
      } else {
        await createTutorCertificate({
          ...payload,
          certificateUrl: uploadedReference || text('certificateUrl')
        });
        setNotice('Đã tải chứng chỉ lên và gửi để xét duyệt.');
      }
      form.reset();
      setEditing(null);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof Error
            ? caught.message
            : 'Không thể lưu chứng chỉ.'
      );
    } finally {
      setSaving(false);
    }
  };

  const openDocument = async (certificate: TutorCertificate) => {
    const tab = window.open('about:blank', '_blank');
    if (!tab) {
      setError('Hãy cho phép cửa sổ bật lên để mở tài liệu chứng chỉ.');
      return;
    }
    tab.opener = null;
    setOpeningId(certificate.id);
    setError('');
    try {
      const fresh = await getTutorCertificate(certificate.id);
      if (!fresh.certificateUrl || !/^https?:\/\//i.test(fresh.certificateUrl)) {
        throw new Error('Không tìm thấy đường dẫn chứng chỉ có thể xem. Vui lòng kiểm tra cấu hình lưu trữ tệp.');
      }
      tab.location.replace(fresh.certificateUrl);
    } catch (caught) {
      tab.close();
      setError(caught instanceof Error ? caught.message : 'Không thể mở chứng chỉ.');
    } finally {
      setOpeningId(null);
    }
  };

  const beginEdit = async (certificate: TutorCertificate) => {
    setError('');
    try {
      const detail = await getTutorCertificate(certificate.id);
      setEditing(detail);
      setNotice(`Đang chỉnh sửa chứng chỉ ${detail.name}.`);
      document.getElementById('certificate-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể tải thông tin chứng chỉ.');
    }
  };

  const removeCertificate = async (certificate: TutorCertificate) => {
    if (!window.confirm(`Bạn có chắc muốn xóa chứng chỉ “${certificate.name}” không?`)) return;
    setError('');
    try {
      await deleteTutorCertificate(certificate.id);
      setRevision((value) => value + 1);
      if (editing?.id === certificate.id) setEditing(null);
      setNotice('Đã xóa chứng chỉ.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể xóa chứng chỉ.');
    }
  };

  const cancelEdit = () => {
    setEditing(null);
    setNotice('');
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">CHỨNG CHỈ & ĐỘ TIN CẬY</span>
          <h1>Chứng chỉ</h1>
          <p>Bổ sung chứng chỉ để học viên an tâm hơn khi lựa chọn bạn.</p>
        </div>
        <span className="tutor-profile-badge">Chờ xét duyệt</span>
      </div>
      {notice && (
        <div className="tutor-notice" role="status">
          {notice}
        </div>
      )}
      {(error || certificateQuery.error) && (
        <div className="tutor-error" role="alert">
          {error || certificateQuery.error}
        </div>
      )}
      <div className="tutor-certificate-layout">
        <section className="tutor-panel tutor-form-panel" id="certificate-editor">
          <div className="tutor-panel-heading">
            <span>{editing ? '✎' : '＋'}</span>
            <div>
              <h2>{editing ? 'Chỉnh sửa chứng chỉ' : 'Thêm chứng chỉ'}</h2>
              <p>Chấp nhận PDF, JPEG hoặc PNG, tối đa 10 MB. Chứng chỉ mới hoặc vừa cập nhật sẽ được xét duyệt lại.</p>
            </div>
          </div>
          <form
            className="tutor-form-grid"
            key={`${editing?.id ?? 'new'}-${revision}`}
            onSubmit={(event) => void saveCertificate(event)}
          >
            <label className="tutor-field-full">
              Tên chứng chỉ
              <input
                name="name"
                required
                maxLength={255}
                defaultValue={editing?.name ?? ''}
                placeholder="Ví dụ: IELTS 8.0"
              />
            </label>
            <label className="tutor-field-full">
              Đơn vị cấp
              <input
                name="issuingOrganization"
                maxLength={255}
                defaultValue={editing?.issuingOrganization ?? ''}
                placeholder="Ví dụ: British Council"
              />
            </label>
            <label>
              Ngày cấp
              <input name="issueDate" type="date" defaultValue={editing?.issueDate ?? ''} />
            </label>
            <label>
              Ngày hết hạn
              <input name="expiryDate" type="date" defaultValue={editing?.expiryDate ?? ''} />
            </label>
            <label className="tutor-field-full">
              Mô tả
              <input
                name="description"
                maxLength={255}
                defaultValue={editing?.description ?? ''}
                placeholder="Thông tin bổ sung (không bắt buộc)"
              />
            </label>
            <label className="tutor-field-full tutor-file-input">
              Thay tệp chứng chỉ
              <input name="file" type="file" accept="application/pdf,image/jpeg,image/png" />
            </label>
            <label className="tutor-field-full">
              Hoặc đường dẫn chứng chỉ
              <input
                name="certificateUrl"
                type="url"
                maxLength={500}
                defaultValue={editing?.certificateUrl?.startsWith('http') ? editing.certificateUrl : ''}
                placeholder="https://…"
              />
            </label>
            <div className="tutor-field-full tutor-upload-actions">
              <div>
                {editing && (
                  <button className="tutor-button secondary" type="button" onClick={cancelEdit}>
                    Hủy chỉnh sửa
                  </button>
                )}
              </div>
              <button className="tutor-button" type="submit" disabled={saving}>
                {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tải lên và gửi'}
              </button>
            </div>
          </form>
        </section>
        <section className="tutor-panel tutor-cert-list-panel">
          <div className="tutor-panel-heading">
            <span>✓</span>
            <div>
              <h2>Chứng chỉ của bạn</h2>
              <p>Theo dõi trạng thái xét duyệt và các tài liệu đã gửi.</p>
            </div>
          </div>
          <label className="tutor-certificate-filter">
            Lọc theo trạng thái
            <select value={filter} onChange={(event) => setFilter(event.target.value as CertificateFilter)}>
              {statuses.map((status) => (
                <option value={status} key={status}>
                  {status === 'ALL' ? 'Tất cả chứng chỉ' : tutorStatusLabel(status)}
                </option>
              ))}
            </select>
          </label>
          {loading ? (
            <p className="tutor-muted">Đang tải chứng chỉ…</p>
          ) : certificates.length === 0 ? (
            <div className="tutor-empty compact">
              <span>▤</span>
              <h3>Chưa có chứng chỉ trong danh sách này</h3>
              <p>Chứng chỉ đã tải lên sẽ hiển thị tại đây.</p>
            </div>
          ) : (
            <div className="tutor-cert-list">
              {certificates.map((certificate) => (
                <article className="tutor-cert-card" key={certificate.id}>
                  <span className="tutor-cert-icon">▤</span>
                  <div className="tutor-cert-info">
                    <div className="tutor-cert-title">
                      <h3>{certificate.name}</h3>
                      <span className={`tutor-cert-status status-${certificate.status.toLowerCase()}`}>
                        {tutorStatusLabel(certificate.status)}
                      </span>
                    </div>
                    <p>
                      {certificate.issuingOrganization || 'Chứng chỉ'}
                      {certificate.issueDate ? ` · Ngày cấp ${certificate.issueDate}` : ''}
                    </p>
                    {certificate.rejectionReason && (
                      <small className="tutor-rejection">Lý do từ chối: {certificate.rejectionReason}</small>
                    )}
                    <div className="tutor-cert-actions">
                      <button
                        className="tutor-detail-link"
                        type="button"
                        disabled={openingId === certificate.id}
                        onClick={() => void openDocument(certificate)}
                      >
                        {openingId === certificate.id ? 'Đang mở…' : 'Xem tài liệu ↗'}
                      </button>
                      {certificate.status !== 'VERIFIED' && (
                        <button className="tutor-detail-link" type="button" onClick={() => void beginEdit(certificate)}>
                          Chỉnh sửa
                        </button>
                      )}
                    </div>
                  </div>
                  <button
                    className="tutor-icon-button"
                    type="button"
                    aria-label={`Xóa chứng chỉ ${certificate.name}`}
                    onClick={() => void removeCertificate(certificate)}
                  >
                    ×
                  </button>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  );
}
