// Danh sách tài liệu kho tri thức + bộ lọc + cờ "có nháp", "còn câu hỏi mở" (docs/support-chatbot/08 mục 8.2).
import { useCallback, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useResource } from '../../../hooks/useResource';
import { listDocuments } from '../../../services/adminAssistantApi';
import { AdminState, FilterChips, StatusBadge } from '../components/AdminUi';
import { formatDateTime } from '../adminFormat';
import { documentStatusMeta, kbAudienceLabels, kbFeatures, kbTypeLabels, kbTypes } from './kbFormat';
import './kb-admin.css';

const statusFilters = ['ALL', 'DRAFT', 'PUBLISHED', 'ARCHIVED'] as const;
type StatusFilter = (typeof statusFilters)[number];

export function KbDocumentListPage() {
  const [params, setParams] = useSearchParams();
  const status = (statusFilters.find((value) => value === params.get('status')) ?? 'ALL') as StatusFilter;
  const type = params.get('type') ?? '';
  const feature = params.get('feature') ?? '';
  const keyword = params.get('q') ?? '';

  const load = useCallback(
    (signal: AbortSignal) =>
      listDocuments({ status: status === 'ALL' ? undefined : status, type, feature, q: keyword }, signal),
    [feature, keyword, status, type]
  );
  const { data, error, isLoading } = useResource(load);

  const update = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) => (value ? next.set(key, value) : next.delete(key)));
    setParams(next);
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    update({
      q: String(form.get('q') ?? '').trim(),
      type: String(form.get('type') ?? ''),
      feature: String(form.get('feature') ?? '')
    });
  };

  return (
    <div className="admin-page">
      <div className="admin-section-heading">
        <h1 className="admin-title">Kho tri thức trợ lý</h1>
        <Link className="admin-button" to="/admin/assistant/documents/new">
          Tạo tài liệu
        </Link>
      </div>
      <FilterChips<StatusFilter>
        label="Trạng thái tài liệu"
        value={status}
        options={statusFilters.map((value) => ({
          value,
          label: value === 'ALL' ? 'Tất cả' : documentStatusMeta[value].label
        }))}
        onChange={(value) => update({ status: value === 'ALL' ? undefined : value })}
      />
      <section className="admin-card">
        <form key={params.toString()} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="q" type="search" defaultValue={keyword} placeholder="Mã hoặc tiêu đề" />
          </label>
          <label>
            Loại
            <select name="type" defaultValue={type}>
              <option value="">Tất cả loại</option>
              {kbTypes.map((value) => (
                <option key={value} value={value}>
                  {kbTypeLabels[value]}
                </option>
              ))}
            </select>
          </label>
          <label>
            Chức năng
            <select name="feature" defaultValue={feature}>
              <option value="">Tất cả</option>
              {kbFeatures.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
          <div className="admin-filter-actions">
            <button className="admin-button" type="submit">
              Tìm
            </button>
          </div>
        </form>
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.content.length === 0 ? (
          <AdminState empty="Không có tài liệu phù hợp." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Tài liệu</th>
                  <th>Loại</th>
                  <th>Đối tượng</th>
                  <th>Trạng thái</th>
                  <th>Sửa lần cuối</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <div className="admin-stack">
                        <strong>{row.title}</strong>
                        <span>{row.docKey}</span>
                        <div className="kb-flags">
                          {row.hasDraft && <span className="admin-badge admin-badge-pending">Có nháp</span>}
                          {row.openTodo > 0 && (
                            <span className="admin-badge admin-badge-danger">Còn {row.openTodo} câu hỏi mở</span>
                          )}
                          {row.publishedWithOpenTodo && (
                            <span className="admin-badge admin-badge-danger">Công bố khi còn câu hỏi mở</span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td className="admin-nowrap">{kbTypeLabels[row.type]}</td>
                    <td>{row.audience.map((value) => kbAudienceLabels[value]).join(', ')}</td>
                    <td>
                      <StatusBadge meta={documentStatusMeta[row.status]} />
                      {row.publishedVersion && <div className="kb-muted">v{row.publishedVersion}</div>}
                    </td>
                    <td className="admin-nowrap">{formatDateTime(row.editedAt)}</td>
                    <td className="admin-cell-action">
                      <Link className="admin-button small secondary" to={`/admin/assistant/documents/${row.id}`}>
                        Mở
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="admin-pagination">
              <span>{data.totalElements} tài liệu</span>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
