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
import { dateLabel, statusLabel } from '../../utils/labels';

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
        setError('Hãy tải tệp lên hoặc nhập đường dẫn chứng chỉ.');
        return;
      }
      if (editing) {
        await updateTutorCertificate(editing.id, {
          ...payload,
          ...(!uploadedReference && text('certificateUrl') ? { certificateUrl: text('certificateUrl') } : {}),
          certificateUrl: uploadedReference || text('certificateUrl') || undefined
        });
        setNotice('Đã cập nhật chứng chỉ và gửi duyệt.');
      } else {
        await createTutorCertificate({
          ...payload,
          certificateUrl: uploadedReference || text('certificateUrl')
        });
        setNotice('Đã tải chứng chỉ lên và gửi duyệt.');
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
            : 'Chưa lưu được chứng chỉ.'
      );
    } finally {
      setSaving(false);
    }
  };

  const openDocument = async (certificate: TutorCertificate) => {
    const tab = window.open('about:blank', '_blank');
    if (!tab) {
      setError('Hãy cho phép cửa sổ bật lên để mở tệp chứng chỉ.');
      return;
    }
    tab.opener = null;
    setOpeningId(certificate.id);
    setError('');
    try {
      const fresh = await getTutorCertificate(certificate.id);
      if (!fresh.certificateUrl || !/^https?:\/\//i.test(fresh.certificateUrl)) {
        throw new Error('Chưa có đường dẫn xem được cho chứng chỉ này.');
      }
      tab.location.replace(fresh.certificateUrl);
    } catch (caught) {
      tab.close();
      setError(caught instanceof Error ? caught.message : 'Chưa mở được chứng chỉ.');
    } finally {
      setOpeningId(null);
    }
  };

  const beginEdit = async (certificate: TutorCertificate) => {
    setError('');
    try {
      const detail = await getTutorCertificate(certificate.id);
      setEditing(detail);
      setNotice(`Đang sửa ${detail.name}.`);
      document.getElementById('certificate-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không tải được thông tin chứng chỉ.');
    }
  };

  const removeCertificate = async (certificate: TutorCertificate) => {
    if (!window.confirm(`Xoá “${certificate.name}”?`)) return;
    setError('');
    try {
      await deleteTutorCertificate(certificate.id);
      setRevision((value) => value + 1);
      if (editing?.id === certificate.id) setEditing(null);
      setNotice('Đã xoá chứng chỉ.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Chưa xoá được chứng chỉ.');
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
          <span className="tutor-eyebrow">BẰNG CẤP</span>
          <h1>Chứng chỉ</h1>
          <p>Thêm bằng cấp để học viên yên tâm chọn bạn.</p>
        </div>
        <span className="tutor-profile-badge">Chỉ quản trị viên xem</span>
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
              <h2>{editing ? 'Sửa chứng chỉ' : 'Thêm chứng chỉ'}</h2>
              <p>PDF, JPEG hoặc PNG, tối đa 10 MB. Chứng chỉ mới hoặc đã sửa sẽ được duyệt lại.</p>
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
                placeholder="Không bắt buộc"
              />
            </label>
            <label className="tutor-field-full tutor-file-input">
              {editing ? 'Thay tệp chứng chỉ' : 'Tệp chứng chỉ'}
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
                    Huỷ sửa
                  </button>
                )}
              </div>
              <button className="tutor-button" type="submit" disabled={saving}>
                {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tải lên và gửi duyệt'}
              </button>
            </div>
          </form>
        </section>
        <section className="tutor-panel tutor-cert-list-panel">
          <div className="tutor-panel-heading">
            <span>✓</span>
            <div>
              <h2>Chứng chỉ của bạn</h2>
            </div>
          </div>
          <label className="tutor-certificate-filter">
            Trạng thái
            <select value={filter} onChange={(event) => setFilter(event.target.value as CertificateFilter)}>
              {statuses.map((status) => (
                <option value={status} key={status}>
                  {status === 'ALL' ? 'Tất cả' : label(status)}
                </option>
              ))}
            </select>
          </label>
          {loading ? (
            <p className="tutor-muted">Đang tải chứng chỉ…</p>
          ) : certificates.length === 0 ? (
            <div className="tutor-empty compact">
              <span>▤</span>
              <h3>Không có chứng chỉ nào ở mục này</h3>
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
                        {label(certificate.status)}
                      </span>
                    </div>
                    <p>
                      {certificate.issuingOrganization || 'Chứng chỉ'}
                      {certificate.issueDate ? ` · Cấp ngày ${dateLabel(certificate.issueDate)}` : ''}
                    </p>
                    {certificate.rejectionReason && (
                      <small className="tutor-rejection">Lý do: {certificate.rejectionReason}</small>
                    )}
                    <div className="tutor-cert-actions">
                      <button
                        className="tutor-detail-link"
                        type="button"
                        disabled={openingId === certificate.id}
                        onClick={() => void openDocument(certificate)}
                      >
                        {openingId === certificate.id ? 'Đang mở…' : 'Xem tệp ↗'}
                      </button>
                      {certificate.status !== 'VERIFIED' && (
                        <button className="tutor-detail-link" type="button" onClick={() => void beginEdit(certificate)}>
                          Sửa
                        </button>
                      )}
                    </div>
                  </div>
                  <button
                    className="tutor-icon-button"
                    type="button"
                    aria-label={`Xoá ${certificate.name}`}
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

function label(value: string) {
  return statusLabel(value, 'certificate');
}
