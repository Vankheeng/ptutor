import { useCallback, useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getAdminStudyingRequests,
  getAdminSubjects,
  getAdminTeachingRequests,
  type ReviewKind,
  type ReviewStatus
} from '../../services/adminRequestReviewApi';
import { formatCurrency } from '../../utils/formatters';
import { formatDateTime } from './adminFormat';
import { AdminPagination, AdminState, FilterChips, StatusBadge } from './components/AdminUi';
import { RequestReviewModal } from './components/RequestReviewModal';
import { formatPriceRange, modeLabels, normalizeName, reviewStatusMeta, reviewStatuses } from './requestReviewFormat';
import './request-review.css';

interface Row {
  id: string;
  personName: string;
  personEmail: string;
  title: string;
  subject: { label: string; tone: 'pending' | 'brand' };
  hasNote: boolean;
  grade: string | null;
  price: string;
  mode: string;
  status: ReviewStatus;
  date: string | null;
}

const copy: Record<ReviewKind, { title: string; person: string; keyword: string }> = {
  teaching: { title: 'Duyệt yêu cầu dạy', person: 'Gia sư', keyword: 'Tên, email gia sư hoặc tiêu đề' },
  studying: { title: 'Duyệt yêu cầu học', person: 'Học sinh', keyword: 'Tên, email học sinh hoặc tiêu đề' }
};

export function AdminRequestReviewPage({ kind }: { kind: ReviewKind }) {
  const [params, setParams] = useSearchParams();
  const status = reviewStatuses.find((value) => value === params.get('status')) ?? 'PENDING_REVIEW';
  const keyword = params.get('keyword') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [openId, setOpenId] = useState<string | null>(null);
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState('');

  const { data: subjects } = useResource(getAdminSubjects, revision);
  const load = useCallback(
    async (signal: AbortSignal) => {
      const query = { status, keyword: keyword || undefined, page };
      if (kind === 'teaching') {
        const result = await getAdminTeachingRequests(query, signal);
        return { ...result, rows: result.content.map((request) => ({ kind: 'teaching' as const, request })) };
      }
      const result = await getAdminStudyingRequests(query, signal);
      return { ...result, rows: result.content.map((request) => ({ kind: 'studying' as const, request })) };
    },
    [keyword, kind, page, status]
  );
  const { data, error, isLoading } = useResource(load, revision);

  const catalogue = new Set((subjects ?? []).map((subject) => normalizeName(subject.name)));
  const rows: Row[] = (data?.rows ?? []).map((row) => {
    if (row.kind === 'teaching') {
      const request = row.request;
      // The summary reports the custom name as subjectName; only a catalogue name means it is resolved.
      const custom =
        Boolean(subjects) && request.status === 'PENDING_REVIEW' && !catalogue.has(normalizeName(request.subjectName));
      return {
        id: request.id,
        personName: request.tutorName,
        personEmail: request.tutorEmail,
        title: request.title,
        subject: custom
          ? { label: `Môn mới: ${request.subjectName ?? '—'}`, tone: 'pending' }
          : { label: `Môn: ${request.subjectName ?? '—'}`, tone: 'brand' },
        hasNote: Boolean(request.note),
        grade: null,
        price: formatCurrency(request.expectedPrice),
        mode: modeLabels[request.teachingMode],
        status: request.status,
        date: request.status === 'PENDING_REVIEW' ? request.createdAt : request.reviewedAt
      };
    }
    const request = row.request;
    return {
      id: request.id,
      personName: request.studentName,
      personEmail: request.studentEmail,
      title: request.title,
      subject: request.subjectName
        ? { label: `Môn: ${request.subjectName}`, tone: 'brand' }
        : { label: `Môn mới: ${request.note ?? '—'}`, tone: 'pending' },
      hasNote: false,
      grade: request.gradeName,
      price: formatPriceRange(request.minPrice, request.maxPrice),
      mode: modeLabels[request.learningMode],
      status: request.status,
      date: request.status === 'PENDING_REVIEW' ? request.createdAt : request.reviewedAt
    };
  });

  const update = (changes: Record<string, string | number | undefined>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key, value]) =>
      value === undefined || value === '' ? next.delete(key) : next.set(key, String(value))
    );
    setParams(next);
    setNotice('');
  };

  const search = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    update({ keyword: String(new FormData(event.currentTarget).get('keyword') ?? '').trim(), page: undefined });
  };

  const showGrade = kind === 'studying';

  return (
    <div className="admin-page">
      <h1 className="admin-title">{copy[kind].title}</h1>
      {notice && (
        <div className="admin-alert admin-alert-success" role="status">
          {notice}
        </div>
      )}
      <FilterChips<ReviewStatus>
        label="Trạng thái"
        value={status}
        options={reviewStatuses.map((value) => ({ value, label: reviewStatusMeta[value].label }))}
        onChange={(value) => update({ status: value === 'PENDING_REVIEW' ? undefined : value, page: undefined })}
      />
      <section className="admin-card">
        <form key={keyword} className="admin-filters" onSubmit={search}>
          <label className="admin-filter-grow">
            Từ khóa
            <input name="keyword" type="search" defaultValue={keyword} placeholder={copy[kind].keyword} />
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
        ) : rows.length === 0 ? (
          <AdminState
            empty={
              status === 'PENDING_REVIEW' && !keyword ? 'Không còn yêu cầu nào chờ duyệt.' : 'Không có yêu cầu nào.'
            }
          />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>{copy[kind].person}</th>
                  <th>Bài đăng</th>
                  {showGrade && <th>Lớp</th>}
                  <th>Học phí</th>
                  <th>Hình thức</th>
                  <th>{status === 'PENDING_REVIEW' ? 'Ngày gửi' : 'Ngày xử lý'}</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      <div className="admin-stack">
                        <strong>{row.personName}</strong>
                        <span>{row.personEmail}</span>
                      </div>
                    </td>
                    <td>
                      <div className="review-title-cell">
                        <span>{row.title}</span>
                        <div className="review-title-badges">
                          <span className={`admin-badge admin-badge-${row.subject.tone}`}>{row.subject.label}</span>
                          {row.hasNote && <span className="admin-badge admin-badge-neutral">Có ghi chú</span>}
                          {status !== 'PENDING_REVIEW' && <StatusBadge meta={reviewStatusMeta[row.status]} />}
                        </div>
                      </div>
                    </td>
                    {showGrade && <td className="admin-nowrap">{row.grade}</td>}
                    <td className="admin-nowrap">{row.price}</td>
                    <td className="admin-nowrap">{row.mode}</td>
                    <td className="admin-nowrap">{formatDateTime(row.date)}</td>
                    <td className="admin-cell-action">
                      <button
                        className={`admin-button small admin-nowrap${row.status === 'PENDING_REVIEW' ? '' : ' secondary'}`}
                        type="button"
                        onClick={() => setOpenId(row.id)}
                      >
                        {row.status === 'PENDING_REVIEW' ? 'Xem và duyệt' : 'Xem chi tiết'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {data && (
          <div className="admin-card-footer">
            <AdminPagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              unit="yêu cầu"
              onChange={(next) => update({ page: next })}
            />
          </div>
        )}
      </section>
      {openId && (
        <RequestReviewModal
          kind={kind}
          id={openId}
          onClose={() => setOpenId(null)}
          onReviewed={(message) => {
            setOpenId(null);
            setNotice(message);
            setRevision((value) => value + 1);
          }}
        />
      )}
    </div>
  );
}
