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
        setError('Upload a document or enter a certificate URL.');
        return;
      }
      if (editing) {
        await updateTutorCertificate(editing.id, {
          ...payload,
          ...(!uploadedReference && text('certificateUrl') ? { certificateUrl: text('certificateUrl') } : {}),
          certificateUrl: uploadedReference || text('certificateUrl') || undefined
        });
        setNotice('Certificate updated and sent for review.');
      } else {
        await createTutorCertificate({
          ...payload,
          certificateUrl: uploadedReference || text('certificateUrl')
        });
        setNotice('Certificate uploaded and sent for review.');
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
            : 'Could not save your certificate.'
      );
    } finally {
      setSaving(false);
    }
  };

  const openDocument = async (certificate: TutorCertificate) => {
    const tab = window.open('about:blank', '_blank');
    if (!tab) {
      setError('Allow pop-ups to open the certificate document.');
      return;
    }
    tab.opener = null;
    setOpeningId(certificate.id);
    setError('');
    try {
      const fresh = await getTutorCertificate(certificate.id);
      if (!fresh.certificateUrl || !/^https?:\/\//i.test(fresh.certificateUrl)) {
        throw new Error(
          'A viewable certificate URL is not available. Check the configured object-storage public endpoint.'
        );
      }
      tab.location.replace(fresh.certificateUrl);
    } catch (caught) {
      tab.close();
      setError(caught instanceof Error ? caught.message : 'Could not open the certificate.');
    } finally {
      setOpeningId(null);
    }
  };

  const beginEdit = async (certificate: TutorCertificate) => {
    setError('');
    try {
      const detail = await getTutorCertificate(certificate.id);
      setEditing(detail);
      setNotice(`Editing ${detail.name}.`);
      document.getElementById('certificate-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not retrieve certificate details.');
    }
  };

  const removeCertificate = async (certificate: TutorCertificate) => {
    if (!window.confirm(`Delete “${certificate.name}”?`)) return;
    setError('');
    try {
      await deleteTutorCertificate(certificate.id);
      setRevision((value) => value + 1);
      if (editing?.id === certificate.id) setEditing(null);
      setNotice('Certificate deleted.');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not delete this certificate.');
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
          <span className="tutor-eyebrow">CREDENTIALS & TRUST</span>
          <h1>Certificates</h1>
          <p>Add credentials that help students feel confident choosing you.</p>
        </div>
        <span className="tutor-profile-badge">Private review</span>
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
              <h2>{editing ? 'Edit certificate' : 'Add a certificate'}</h2>
              <p>PDF, JPEG or PNG, up to 10 MB. New or updated certificates return to review.</p>
            </div>
          </div>
          <form
            className="tutor-form-grid"
            key={`${editing?.id ?? 'new'}-${revision}`}
            onSubmit={(event) => void saveCertificate(event)}
          >
            <label className="tutor-field-full">
              Certificate name
              <input
                name="name"
                required
                maxLength={255}
                defaultValue={editing?.name ?? ''}
                placeholder="e.g. IELTS 8.0"
              />
            </label>
            <label className="tutor-field-full">
              Issuing organization
              <input
                name="issuingOrganization"
                maxLength={255}
                defaultValue={editing?.issuingOrganization ?? ''}
                placeholder="e.g. British Council"
              />
            </label>
            <label>
              Issue date
              <input name="issueDate" type="date" defaultValue={editing?.issueDate ?? ''} />
            </label>
            <label>
              Expiry date
              <input name="expiryDate" type="date" defaultValue={editing?.expiryDate ?? ''} />
            </label>
            <label className="tutor-field-full">
              Description
              <input
                name="description"
                maxLength={255}
                defaultValue={editing?.description ?? ''}
                placeholder="Optional details"
              />
            </label>
            <label className="tutor-field-full tutor-file-input">
              Replace certificate file
              <input name="file" type="file" accept="application/pdf,image/jpeg,image/png" />
            </label>
            <label className="tutor-field-full">
              Or certificate URL
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
                    Cancel edit
                  </button>
                )}
              </div>
              <button className="tutor-button" type="submit" disabled={saving}>
                {saving ? 'Saving…' : editing ? 'Save changes' : 'Upload and submit'}
              </button>
            </div>
          </form>
        </section>
        <section className="tutor-panel tutor-cert-list-panel">
          <div className="tutor-panel-heading">
            <span>✓</span>
            <div>
              <h2>Your certificates</h2>
              <p>Review status and submitted documents.</p>
            </div>
          </div>
          <label className="tutor-certificate-filter">
            Filter status
            <select value={filter} onChange={(event) => setFilter(event.target.value as CertificateFilter)}>
              {statuses.map((status) => (
                <option value={status} key={status}>
                  {status === 'ALL' ? 'All certificates' : titleCase(status)}
                </option>
              ))}
            </select>
          </label>
          {loading ? (
            <p className="tutor-muted">Loading certificates…</p>
          ) : certificates.length === 0 ? (
            <div className="tutor-empty compact">
              <span>▤</span>
              <h3>No certificates in this view</h3>
              <p>Uploaded credentials will appear here.</p>
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
                        {titleCase(certificate.status)}
                      </span>
                    </div>
                    <p>
                      {certificate.issuingOrganization || 'Certificate'}
                      {certificate.issueDate ? ` · Issued ${certificate.issueDate}` : ''}
                    </p>
                    {certificate.rejectionReason && (
                      <small className="tutor-rejection">Review note: {certificate.rejectionReason}</small>
                    )}
                    <div className="tutor-cert-actions">
                      <button
                        className="tutor-detail-link"
                        type="button"
                        disabled={openingId === certificate.id}
                        onClick={() => void openDocument(certificate)}
                      >
                        {openingId === certificate.id ? 'Opening…' : 'View document ↗'}
                      </button>
                      {certificate.status !== 'VERIFIED' && (
                        <button className="tutor-detail-link" type="button" onClick={() => void beginEdit(certificate)}>
                          Edit
                        </button>
                      )}
                    </div>
                  </div>
                  <button
                    className="tutor-icon-button"
                    type="button"
                    aria-label={`Delete ${certificate.name}`}
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

function titleCase(value: string) {
  return value
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}
