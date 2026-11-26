// Lịch sử phiên bản: xem bản cũ, khôi phục (= công bố lại nội dung cũ thành phiên bản mới, không sửa lịch sử)
// — docs/support-chatbot/03 mục 3.2, 08 mục 8.2.
import { useState } from 'react';
import { getVersion, restoreVersion, type KbDocumentDetail, type KbVersion } from '../../../services/adminAssistantApi';
import { AdminModal, StatusBadge } from '../components/AdminUi';
import { formatDateTime } from '../adminFormat';
import { versionStateMeta } from './kbFormat';
import { MarkdownPreview } from './MarkdownPreview';

interface Props {
  documentId: string;
  versions: KbVersion[];
  busy: boolean;
  onRestored: (detail: KbDocumentDetail) => void;
}

export function KbVersionHistory({ documentId, versions, busy, onRestored }: Props) {
  const [viewing, setViewing] = useState<KbVersion | null>(null);
  const [error, setError] = useState('');
  const restorable = (version: KbVersion) => version.state === 'SUPERSEDED' || version.state === 'ARCHIVED';

  const open = async (version: number) => {
    setError('');
    try {
      setViewing(await getVersion(documentId, version));
    } catch {
      setError('Không mở được phiên bản này.');
    }
  };

  const restore = async (version: number) => {
    setError('');
    try {
      onRestored(await restoreVersion(documentId, version));
      setViewing(null);
    } catch {
      setError('Không khôi phục được phiên bản này.');
    }
  };

  return (
    <section className="admin-card admin-card-pad">
      <h2 className="kb-side-title">Lịch sử phiên bản</h2>
      {error && <div className="admin-alert admin-alert-danger">{error}</div>}
      <ul className="kb-versions">
        {versions.map((version) => (
          <li key={version.id}>
            <button type="button" className="admin-link" onClick={() => void open(version.version)}>
              v{version.version}
            </button>
            <StatusBadge meta={versionStateMeta[version.state]} />
            <span className="kb-muted">
              {formatDateTime(version.publishedAt ?? version.editedAt)}
              {version.restoredFromVersion ? ` · khôi phục từ v${version.restoredFromVersion}` : ''}
            </span>
          </li>
        ))}
      </ul>
      {viewing && (
        <AdminModal
          title={`Phiên bản ${viewing.version}`}
          subtitle={viewing.title}
          size="xl"
          onClose={() => setViewing(null)}
          footer={
            restorable(viewing) ? (
              <button
                className="admin-button"
                type="button"
                disabled={busy}
                onClick={() => void restore(viewing.version)}
              >
                Khôi phục v{viewing.version}
              </button>
            ) : undefined
          }
        >
          <MarkdownPreview markdown={viewing.contentMd ?? ''} />
        </AdminModal>
      )}
    </section>
  );
}
