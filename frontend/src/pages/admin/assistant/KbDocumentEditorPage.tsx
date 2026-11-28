// Trang soạn tài liệu kho tri thức (docs/support-chatbot/08-giao-dien.md mục 8.2, vòng đời 03 mục 3.2):
// form thông tin + ô soạn Markdown | xem trước + cách chia đoạn + lịch sử phiên bản.
// - Còn câu hỏi mở (todo) → nút Công bố bị khoá.
// - Công bố: kiểm tra hợp lệ → hộp thoại xác nhận → công bố. Sai sau khi công bố → khôi phục phiên bản trước.
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../services/httpClient';
import {
  archiveDocument,
  createDocument,
  discardDraft,
  getDocument,
  previewDocument,
  publishDocument,
  saveDraft,
  validateDraft,
  type KbAudience,
  type KbDocumentDetail,
  type KbDocumentPayload,
  type KbVersion,
  type ValidationResult
} from '../../../services/adminAssistantApi';
import { AdminModal, AdminState, StatusBadge } from '../components/AdminUi';
import { documentStatusMeta, kbAudienceLabels, kbAudiences, kbFeatures, kbTypeLabels, kbTypes } from './kbFormat';
import { KbVersionHistory } from './KbVersionHistory';
import { MarkdownPreview } from './MarkdownPreview';
import './kb-admin.css';

const MAX_CHUNK_TOKENS = 400;

function emptyPayload(question = ''): KbDocumentPayload {
  return {
    docKey: '',
    title: '',
    type: '',
    audience: [],
    features: [],
    routes: [],
    keywords: [],
    errorCodes: [],
    sourceRefs: [],
    todo: [],
    contentMd: question ? `# Tiêu đề\n\n## ${question}\n\n` : '# Tiêu đề\n\n## Mục đầu tiên\n\n'
  };
}

function fromVersion(version: KbVersion): KbDocumentPayload {
  return {
    title: version.title,
    type: version.type,
    audience: version.audience,
    features: version.features,
    routes: version.routes,
    keywords: version.keywords,
    errorCodes: version.errorCodes,
    sourceRefs: version.sourceRefs,
    todo: version.todo,
    contentMd: version.contentMd ?? ''
  };
}

const splitList = (text: string) =>
  text
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? `${error.code}: ${error.message}` : 'Không thực hiện được. Thử lại sau.';
}

export function KbDocumentEditorPage() {
  const { documentId } = useParams();
  const isNew = !documentId;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState<KbDocumentDetail | null>(null);
  const [form, setForm] = useState<KbDocumentPayload>(() => emptyPayload(searchParams.get('question') ?? ''));
  const [preview, setPreview] = useState<ValidationResult | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);
  const [confirmPublish, setConfirmPublish] = useState<ValidationResult | null>(null);

  const applyDetail = useCallback((next: KbDocumentDetail) => {
    setDetail(next);
    const editable = next.draft ?? next.published ?? next.versions[0];
    if (editable?.contentMd !== undefined) setForm({ docKey: next.docKey, ...fromVersion(editable) });
  }, []);

  useEffect(() => {
    if (isNew || !documentId) return;
    const controller = new AbortController();
    getDocument(documentId, controller.signal)
      .then(applyDetail)
      .catch((error) => setMessage({ tone: 'danger', text: errorMessage(error) }))
      .finally(() => setLoading(false));
    return () => controller.abort();
  }, [applyDetail, documentId, isNew]);

  // Xem trước cách chia đoạn khi đang gõ (chậm 600 ms để không gọi API mỗi phím).
  useEffect(() => {
    const timer = window.setTimeout(() => {
      previewDocument(form)
        .then(setPreview)
        .catch(() => setPreview(null));
    }, 600);
    return () => window.clearTimeout(timer);
  }, [form]);

  const run = async (action: () => Promise<KbDocumentDetail>, success: string) => {
    setBusy(true);
    setMessage(null);
    try {
      applyDetail(await action());
      setMessage({ tone: 'success', text: success });
    } catch (error) {
      setMessage({ tone: 'danger', text: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    if (isNew) {
      setBusy(true);
      try {
        const created = await createDocument(form);
        navigate(`/admin/assistant/documents/${created.id}`, { replace: true });
      } catch (error) {
        setMessage({ tone: 'danger', text: errorMessage(error) });
      } finally {
        setBusy(false);
      }
      return;
    }
    await run(() => saveDraft(documentId!, form), 'Đã lưu bản nháp');
  };

  const startPublish = async () => {
    if (!documentId) return;
    setBusy(true);
    try {
      await saveDraft(documentId, form);
      setConfirmPublish(await validateDraft(documentId));
    } catch (error) {
      setMessage({ tone: 'danger', text: errorMessage(error) });
    } finally {
      setBusy(false);
    }
  };

  const set = <K extends keyof KbDocumentPayload>(key: K, value: KbDocumentPayload[K]) =>
    setForm((current) => ({ ...current, [key]: value }));
  const toggle = (key: 'audience' | 'features', value: string) =>
    setForm((current) => {
      const list = current[key] as string[];
      const next = list.includes(value) ? list.filter((item) => item !== value) : [...list, value];
      return { ...current, [key]: next as KbAudience[] };
    });

  if (loading) return <AdminState loading />;
  const openTodo = form.todo.filter(Boolean).length;
  const tooLong = preview?.chunks.some((chunk) => chunk.tokenCount > MAX_CHUNK_TOKENS) ?? false;

  return (
    <div className="admin-page kb-editor">
      <div className="admin-section-heading">
        <div className="admin-stack">
          <Link className="admin-back" to="/admin/assistant/documents">
            ← Kho tri thức
          </Link>
          <h1 className="admin-title">{form.title || 'Tài liệu mới'}</h1>
          {detail && (
            <div className="kb-flags">
              <StatusBadge meta={documentStatusMeta[detail.status]} />
              {detail.draft && (
                <span className="admin-badge admin-badge-pending">Bản nháp v{detail.draft.version}</span>
              )}
              {detail.published && <span className="kb-muted">đang công bố v{detail.published.version}</span>}
              {detail.published?.publishedWithOpenTodo && (
                <span className="admin-badge admin-badge-danger">Công bố khi còn câu hỏi mở</span>
              )}
            </div>
          )}
        </div>
        <div className="admin-button-row">
          <button className="admin-button secondary" type="button" disabled={busy} onClick={() => void save()}>
            Lưu
          </button>
          {!isNew && (
            <button
              className="admin-button"
              type="button"
              disabled={busy || openTodo > 0}
              title={openTodo > 0 ? 'Còn câu hỏi mở chưa trả lời' : undefined}
              onClick={() => void startPublish()}
            >
              Công bố
            </button>
          )}
          {detail?.draft && detail.published && (
            <button
              className="admin-button secondary"
              type="button"
              disabled={busy}
              onClick={() => void run(() => discardDraft(documentId!), 'Đã bỏ bản nháp')}
            >
              Bỏ nháp
            </button>
          )}
          {detail?.status === 'PUBLISHED' && (
            <button
              className="admin-button secondary"
              type="button"
              disabled={busy}
              onClick={() => void run(() => archiveDocument(documentId!), 'Đã lưu trữ tài liệu')}
            >
              Lưu trữ
            </button>
          )}
        </div>
      </div>
      {message && <div className={`admin-alert admin-alert-${message.tone}`}>{message.text}</div>}

      <div className="kb-editor-grid">
        <section className="admin-card admin-card-pad kb-form">
          {isNew && (
            <label className="admin-field">
              Mã tài liệu (kebab-case)
              <input value={form.docKey ?? ''} onChange={(event) => set('docKey', event.target.value)} />
            </label>
          )}
          <label className="admin-field">
            Tiêu đề
            <input value={form.title} onChange={(event) => set('title', event.target.value)} />
          </label>
          <label className="admin-field">
            Loại
            <select
              value={form.type}
              onChange={(event) => set('type', event.target.value as KbDocumentPayload['type'])}
            >
              <option value="">Chọn loại</option>
              {kbTypes.map((value) => (
                <option key={value} value={value}>
                  {kbTypeLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <fieldset className="admin-field kb-checks">
            <legend>Đối tượng</legend>
            {kbAudiences.map((value) => (
              <label key={value}>
                <input
                  type="checkbox"
                  checked={form.audience.includes(value)}
                  onChange={() => toggle('audience', value)}
                />
                {kbAudienceLabels[value]}
              </label>
            ))}
          </fieldset>
          <fieldset className="admin-field kb-checks">
            <legend>Chức năng</legend>
            {kbFeatures.map((value) => (
              <label key={value}>
                <input
                  type="checkbox"
                  checked={form.features.includes(value)}
                  onChange={() => toggle('features', value)}
                />
                {value}
              </label>
            ))}
          </fieldset>
          <label className="admin-field">
            Trang liên quan (cách nhau bằng dấu phẩy)
            <input value={form.routes.join(', ')} onChange={(event) => set('routes', splitList(event.target.value))} />
          </label>
          <label className="admin-field">
            Từ khóa
            <input
              value={form.keywords.join(', ')}
              onChange={(event) => set('keywords', splitList(event.target.value))}
            />
          </label>
          {form.sourceRefs.length > 0 && (
            <details className="kb-sources">
              <summary>Căn cứ trong code ({form.sourceRefs.length})</summary>
              <ul>
                {form.sourceRefs.map((source, index) => (
                  <li key={index}>{typeof source === 'string' ? source : JSON.stringify(source)}</li>
                ))}
              </ul>
            </details>
          )}
          <label className="admin-field">
            Câu hỏi còn mở (mỗi dòng một câu)
            <textarea
              rows={3}
              value={form.todo.join('\n')}
              onChange={(event) => set('todo', event.target.value.split('\n'))}
            />
          </label>
          {openTodo > 0 && (
            <div className="admin-alert admin-alert-warning">Còn {openTodo} câu hỏi cần trả lời trước khi công bố</div>
          )}
          <label className="admin-field">
            Nội dung (Markdown)
            <textarea
              className="kb-content"
              rows={22}
              value={form.contentMd}
              onChange={(event) => set('contentMd', event.target.value)}
            />
          </label>
        </section>

        <aside className="kb-side">
          <section className="admin-card admin-card-pad">
            <h2 className="kb-side-title">Xem trước</h2>
            <MarkdownPreview markdown={form.contentMd} />
          </section>
          <section className="admin-card admin-card-pad">
            <h2 className="kb-side-title">
              Đoạn sau khi chia: {preview?.chunks.length ?? 0} {tooLong ? '⚠' : '✓'}
            </h2>
            <ol className="kb-chunks">
              {preview?.chunks.map((chunk) => (
                <li key={chunk.ordinal} className={chunk.tokenCount > MAX_CHUNK_TOKENS ? 'admin-text-danger' : ''}>
                  {chunk.headingPath} <span className="kb-muted">({chunk.tokenCount} token)</span>
                </li>
              ))}
            </ol>
            {preview?.errors.map((error) => (
              <div key={error} className="admin-text-danger kb-small">
                {error}
              </div>
            ))}
          </section>
          {detail && !isNew && (
            <KbVersionHistory
              documentId={detail.id}
              versions={detail.versions}
              busy={busy}
              onRestored={(next) => {
                applyDetail(next);
                setMessage({ tone: 'success', text: 'Đã khôi phục thành phiên bản mới' });
              }}
            />
          )}
        </aside>
      </div>

      {confirmPublish && (
        <AdminModal
          title="Công bố tài liệu"
          subtitle={confirmPublish.ok ? `${confirmPublish.chunks.length} đoạn` : 'Tài liệu chưa hợp lệ'}
          size="sm"
          onClose={() => setConfirmPublish(null)}
          footer={
            <div className="admin-button-row">
              <button className="admin-button secondary" type="button" onClick={() => setConfirmPublish(null)}>
                Huỷ
              </button>
              <button
                className="admin-button"
                type="button"
                disabled={!confirmPublish.ok || busy}
                onClick={() => {
                  setConfirmPublish(null);
                  void run(() => publishDocument(documentId!), 'Đã công bố');
                }}
              >
                Công bố
              </button>
            </div>
          }
        >
          {confirmPublish.ok ? (
            <p>Bản nháp sẽ thay bản đang dùng ngay sau khi công bố.</p>
          ) : (
            <ul>
              {confirmPublish.errors.map((error) => (
                <li key={error} className="admin-text-danger">
                  {error}
                </li>
              ))}
            </ul>
          )}
        </AdminModal>
      )}
    </div>
  );
}
