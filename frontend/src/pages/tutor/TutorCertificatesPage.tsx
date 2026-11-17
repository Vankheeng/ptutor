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

function normalizeName(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/gi, 'd')
    .toLocaleLowerCase('vi')
    .trim();
}

export function TutorCertificatesPage() {
  const [filter, setFilter] = useState<CertificateFilter>('ALL');
  const [searchName, setSearchName] = useState('');
  const certificateQuery = useTutorCertificates();
  const [savedCertificates, setSavedCertificates] = useState<TutorCertificate[] | null>(null);
  const certificates = (savedCertificates ?? certificateQuery.data ?? []).filter(
    (certificate) => filter === 'ALL' || certificate.status === filter
  );
  const normalizedSearch = normalizeName(searchName);
  const visibleCertificates = normalizedSearch
    ? certificates.filter((certificate) => normalizeName(certificate.name).includes(normalizedSearch))
    : certificates;
  const [editing, setEditing] = useState<TutorCertificate | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [openingId, setOpeningId] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const loading = certificateQuery.isLoading && savedCertificates === null;

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
      const savedCertificate = editing
        ? await updateTutorCertificate(editing.id, {
            ...payload,
            ...(!uploadedReference && text('certificateUrl') ? { certificateUrl: text('certificateUrl') } : {}),
            certificateUrl: uploadedReference || text('certificateUrl') || undefined
          })
        : await createTutorCertificate({
            ...payload,
            certificateUrl: uploadedReference || text('certificateUrl')
          });
      setSavedCertificates((current) => [
        savedCertificate,
        ...(current ?? certificateQuery.data ?? []).filter((item) => item.id !== savedCertificate.id)
      ]);
      setEditing(null);
      setShowForm(false);
      setFilter('ALL');
      setSearchName('');
      setNotice(editing ? 'Đã cập nhật chứng chỉ và gửi để xét duyệt.' : 'Đã tải chứng chỉ lên và gửi để xét duyệt.');
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
      setShowForm(true);
      window.requestAnimationFrame(() =>
        document.getElementById('certificate-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      );
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể tải thông tin chứng chỉ.');
    }
  };

  const removeCertificate = async (certificate: TutorCertificate) => {
    if (!window.confirm(`Bạn có chắc muốn xóa chứng chỉ “${certificate.name}” không?`)) return;
    setError('');
    try {
      await deleteTutorCertificate(certificate.id);
      setSavedCertificates((current) =>
        (current ?? certificateQuery.data ?? []).filter((item) => item.id !== certificate.id)
      );
      if (editing?.id === certificate.id) {
        setEditing(null);
        setShowForm(false);
      }
      setNotice('Đã xóa chứng chỉ.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể xóa chứng chỉ.');
    }
  };

  const cancelEdit = () => {
    setEditing(null);
    setShowForm(false);
    setNotice('');
  };

  const beginCreate = () => {
    setEditing(null);
    setError('');
    setShowForm(true);
    window.requestAnimationFrame(() =>
      document.getElementById('certificate-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    );
  };

  return (
    <>
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
        <div className="tutor-cert-toolbar">
          <button className="tutor-button" type="button" onClick={beginCreate}>
            Thêm chứng chỉ
          </button>
        </div>
        {showForm && (
          <section className="tutor-panel tutor-form-panel tutor-cert-editor" id="certificate-editor">
            <div className="tutor-panel-heading">
              <h2>{editing ? 'Chỉnh sửa chứng chỉ' : 'Thêm chứng chỉ'}</h2>
            </div>
            <form
              className="tutor-form-grid"
              key={editing?.id ?? 'new'}
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
                <button className="tutor-button secondary" type="button" onClick={cancelEdit}>
                  Hủy
                </button>
                <button className="tutor-button" type="submit" disabled={saving}>
                  {saving ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tải lên và gửi'}
                </button>
              </div>
            </form>
          </section>
        )}
        <section className="tutor-panel tutor-cert-list-panel">
          <div className="tutor-certificate-filters">
            <label className="tutor-certificate-filter">
              Tìm theo tên
              <input
                type="search"
                value={searchName}
                onChange={(event) => setSearchName(event.target.value)}
                placeholder="Tên chứng chỉ"
              />
            </label>
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
          </div>
          <div className="tutor-cert-table-wrap">
            <table className="tutor-cert-table">
              <thead>
                <tr>
                  <th>Tên chứng chỉ</th>
                  <th>Đơn vị cấp</th>
                  <th>Ngày cấp</th>
                  <th>Hết hạn</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={6} className="tutor-cert-table-empty">
                      Đang tải chứng chỉ…
                    </td>
                  </tr>
                ) : visibleCertificates.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="tutor-cert-table-empty">
                      {normalizedSearch ? 'Không tìm thấy chứng chỉ' : 'Chưa có chứng chỉ'}
                    </td>
                  </tr>
                ) : (
                  visibleCertificates.map((certificate) => (
                    <tr key={certificate.id}>
                      <td>
                        <strong>{certificate.name}</strong>
                        {certificate.rejectionReason && (
                          <small className="tutor-rejection">Lý do từ chối: {certificate.rejectionReason}</small>
                        )}
                      </td>
                      <td>{certificate.issuingOrganization || '—'}</td>
                      <td>{certificate.issueDate || '—'}</td>
                      <td>{certificate.expiryDate || '—'}</td>
                      <td>
                        <span className={`tutor-cert-status status-${certificate.status.toLowerCase()}`}>
                          {tutorStatusLabel(certificate.status)}
                        </span>
                      </td>
                      <td>
                        <div className="tutor-cert-table-actions">
                          <button
                            type="button"
                            disabled={openingId === certificate.id}
                            onClick={() => void openDocument(certificate)}
                          >
                            {openingId === certificate.id ? 'Đang mở…' : 'Xem tài liệu'}
                          </button>
                          <button type="button" onClick={() => void beginEdit(certificate)}>
                            Chỉnh sửa
                          </button>
                          <button className="danger" type="button" onClick={() => void removeCertificate(certificate)}>
                            Xóa
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
