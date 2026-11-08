import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import {
  getMyTeachingRequests,
  getTeachingRequestApplications,
  updateTeachingRequestApplication,
  type StudentTeachingApplication
} from '../../services/myTeachingRequestApi';
import { getSubjects } from '../../services/subjectApi';
import type { TeachingRequest } from '../../types/api';
import { RequestOfferActions } from '../../components/common-user/RequestOfferActions';

type Status = StudentTeachingApplication['status'];
type Mode = StudentTeachingApplication['learningMode'];
type RequestWithContext = StudentTeachingApplication & { teachingRequest: TeachingRequest };
const statuses: Array<'ALL' | Status> = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'];

export function TutorProposalsPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<'ALL' | Status>('ALL');
  const [mode, setMode] = useState<'ALL' | Mode>('ALL');
  const [requestId, setRequestId] = useState('ALL');
  const [student, setStudent] = useState('');
  const [subject, setSubject] = useState('');
  const [search, setSearch] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const load = useCallback(async (signal: AbortSignal) => {
    const [requests, subjects] = await Promise.all([getMyTeachingRequests(signal), getSubjects('name', signal)]);
    const groups = await Promise.all(
      requests.map(async (teachingRequest) => {
        const applications = await getTeachingRequestApplications(teachingRequest.id, signal);
        return applications.map((application) => ({ ...application, teachingRequest }));
      })
    );
    return { requests, subjects, applications: groups.flat() };
  }, []);
  const query = useResource(load, revision);
  const requests = query.data?.requests ?? [];
  const filtered = useMemo(() => {
    const studentTerm = student.trim().toLocaleLowerCase('vi-VN');
    const subjectTerm = subject.trim().toLocaleLowerCase('vi-VN');
    const searchTerm = search.trim().toLocaleLowerCase('vi-VN');
    return (query.data?.applications ?? [])
      .filter((item) => {
        const studentName = [item.studentFirstName, item.studentLastName]
          .filter(Boolean)
          .join(' ')
          .toLocaleLowerCase('vi-VN');
        const subjectName = (
          item.teachingRequest.subjectName ||
          item.teachingRequest.customSubjectName ||
          ''
        ).toLocaleLowerCase('vi-VN');
        const searchable = [
          studentName,
          item.studentEmail,
          subjectName,
          item.teachingRequest.title,
          item.message,
          item.preferredSchedule
        ]
          .filter(Boolean)
          .join(' ')
          .toLocaleLowerCase('vi-VN');
        const date = item.createdAt.slice(0, 10);
        return (
          (status === 'ALL' || item.status === status) &&
          (mode === 'ALL' || item.learningMode === mode) &&
          (requestId === 'ALL' || item.teachingRequestId === requestId) &&
          (!studentTerm || studentName.includes(studentTerm)) &&
          (!subjectTerm || subjectName.includes(subjectTerm)) &&
          (!searchTerm || searchable.includes(searchTerm)) &&
          (!from || date >= from) &&
          (!to || date <= to)
        );
      })
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  }, [from, mode, query.data?.applications, requestId, search, status, student, subject, to]);
  const pageSize = 8;
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize));
  const pageItems = filtered.slice(page * pageSize, (page + 1) * pageSize);
  const resetPage = () => setPage(0);
  const changeStatus = async (item: RequestWithContext, next: 'ACCEPTED' | 'REJECTED') => {
    setBusy(true);
    setError('');
    try {
      await updateTeachingRequestApplication(item.teachingRequestId, item.id, next);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật đề nghị.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="tutor-page-heading tutor-proposals-heading">
        <div>
          <h1>Đề nghị từ học viên</h1>
        </div>
        <div className="tutor-heading-actions">
          <span className="tutor-count">{filtered.length} đề nghị</span>
        </div>
      </div>
      {(error || query.error) && (
        <div className="tutor-error" role="alert">
          {error || query.error}
        </div>
      )}
      <section className="tutor-panel tutor-proposals-filter-panel" aria-label="Bộ lọc đề nghị">
        <div className="tutor-proposals-filter-grid">
          <Filter label="Từ ngày">
            <input
              type="date"
              value={from}
              onChange={(event) => {
                setFrom(event.target.value);
                resetPage();
              }}
            />
          </Filter>
          <Filter label="Đến ngày">
            <input
              type="date"
              value={to}
              onChange={(event) => {
                setTo(event.target.value);
                resetPage();
              }}
            />
          </Filter>
          <Filter label="Yêu cầu">
            <select
              value={requestId}
              onChange={(event) => {
                setRequestId(event.target.value);
                resetPage();
              }}
            >
              <option value="ALL">Tất cả yêu cầu</option>
              {requests.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title || item.subjectName || 'Yêu cầu dạy học'}
                </option>
              ))}
            </select>
          </Filter>
          <Filter label="Học viên">
            <input
              value={student}
              onChange={(event) => {
                setStudent(event.target.value);
                resetPage();
              }}
              placeholder="Tên học viên"
            />
          </Filter>
          <Filter label="Môn học">
            <select
              value={subject}
              onChange={(event) => {
                setSubject(event.target.value);
                resetPage();
              }}
            >
              <option value="">Tất cả môn học</option>
              {(query.data?.subjects ?? []).map((item) => (
                <option key={item.id} value={item.name}>
                  {item.name}
                </option>
              ))}
            </select>
          </Filter>
          <Filter label="Trạng thái">
            <select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as 'ALL' | Status);
                resetPage();
              }}
            >
              {statuses.map((item) => (
                <option key={item} value={item}>
                  {item === 'ALL' ? 'Tất cả trạng thái' : statusLabel(item)}
                </option>
              ))}
            </select>
          </Filter>
          <Filter label="Hình thức học">
            <select
              value={mode}
              onChange={(event) => {
                setMode(event.target.value as 'ALL' | Mode);
                resetPage();
              }}
            >
              <option value="ALL">Tất cả hình thức</option>
              <option value="ONLINE">Trực tuyến</option>
              <option value="OFFLINE">Trực tiếp</option>
            </select>
          </Filter>
          <Filter label="Tìm kiếm">
            <input
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                resetPage();
              }}
              placeholder="Tìm nội dung đề nghị..."
            />
          </Filter>
        </div>
      </section>
      {query.isLoading ? (
        <div className="tutor-empty">
          <h3>Đang tải đề nghị...</h3>
        </div>
      ) : pageItems.length === 0 ? (
        <div className="tutor-empty">
          <h3>Không tìm thấy đề nghị phù hợp</h3>
          <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.</p>
        </div>
      ) : (
        <section className="tutor-proposals-table-wrap">
          <table className="tutor-proposals-table">
            <thead>
              <tr>
                <th>Học viên</th>
                <th>Yêu cầu / Môn học</th>
                <th>Nội dung đề nghị</th>
                <th>Thời gian gửi</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {pageItems.map((item) => (
                <tr key={item.id}>
                  <td>
                    <strong>{studentName(item)}</strong>
                    <small>{item.studentEmail}</small>
                  </td>
                  <td>
                    <strong>{item.teachingRequest.title || 'Đề nghị dạy học'}</strong>
                    <small>
                      {item.teachingRequest.subjectName || item.teachingRequest.customSubjectName || 'Môn học'} ·{' '}
                      {item.gradeName}
                    </small>
                  </td>
                  <td>
                    <span className="tutor-proposal-message">
                      {item.message || item.preferredSchedule || 'Không có lời nhắn'}
                    </span>
                  </td>
                  <td>{formatDateTime(item.createdAt)}</td>
                  <td>
                    <span className={`tutor-proposal-status status-${item.status.toLowerCase()}`}>
                      {statusLabel(item.status)}
                    </span>
                  </td>
                  <td>
                    <div className="tutor-proposal-row-actions">
                      <button
                        className="tutor-button secondary small"
                        type="button"
                        onClick={() => navigate(`/tutor/sections/teaching-requests/${item.teachingRequestId}`)}
                      >
                        Xem chi tiết yêu cầu
                      </button>
                      <RequestOfferActions
                        role="tutor"
                        status={item.status}
                        busy={busy}
                        onAccept={() => void changeStatus(item, 'ACCEPTED')}
                        onReject={() => void changeStatus(item, 'REJECTED')}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
      {!query.isLoading && filtered.length > 0 && (
        <div className="tutor-marketplace-pagination">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            Trước
          </button>
          <span>
            Trang {page + 1}/{pageCount}
          </span>
          <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((value) => value + 1)}>
            Sau
          </button>
        </div>
      )}
    </>
  );
}

function Filter({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="tutor-proposals-filter">
      <span>{label}</span>
      {children}
    </label>
  );
}
function studentName(item: StudentTeachingApplication) {
  return [item.studentFirstName, item.studentLastName].filter(Boolean).join(' ') || item.studentEmail;
}
function statusLabel(value: Status | 'ALL') {
  return { ALL: 'Tất cả', PENDING: 'Chờ xử lý', ACCEPTED: 'Đã chấp nhận', REJECTED: 'Từ chối', CANCELLED: 'Đã hủy' }[
    value
  ];
}
function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
}
