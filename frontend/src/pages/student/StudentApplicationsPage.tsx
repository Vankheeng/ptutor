import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../services/httpClient';
import {
  cancelStudentApplication,
  getStudentApplications,
  getTeachingRequest,
  type ApplicationStatus
} from '../../services/studentApi';
import type { TeachingRequest } from '../../types/api';
import { useResource } from '../../hooks/useResource';
import { ApplicationToolbar } from '../../components/applications/ApplicationToolbar';
import { StudentApplicationCard } from '../../components/student/StudentApplicationCard';
import {
  StudentApplicationForm,
  type StudentApplicationFormValues
} from '../../components/student/StudentApplicationForm';
import { applicationStatusLabel } from '../../components/student/studentApplicationUtils';
import { updateStudentApplication } from '../../services/studentApi';
import '../tutor/tutor.css';

const filters: Array<'ALL' | ApplicationStatus> = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'];
const PAGE_SIZE = 20;

export function StudentApplicationsPage() {
  const [filter, setFilter] = useState<'ALL' | ApplicationStatus>('ALL');
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [editingApplication, setEditingApplication] = useState<string | null>(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState('');
  const [requestDetails, setRequestDetails] = useState<Record<string, TeachingRequest>>({});
  const [requestDetailErrors, setRequestDetailErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setKeyword(search.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [search]);

  const requestKey = `${filter}:${keyword}:${page}:${revision}`;
  const load = useCallback(
    async (signal: AbortSignal) => ({
      key: requestKey,
      page: await getStudentApplications(filter === 'ALL' ? undefined : filter, page, PAGE_SIZE, signal, keyword)
    }),
    [filter, page, keyword, requestKey]
  );
  const query = useResource(load, revision);
  const pageData = query.data?.key === requestKey ? query.data.page : null;
  const applications = useMemo(() => pageData?.content ?? [], [pageData]);
  const editingItem = pageData?.content.find((item) => item.id === editingApplication);

  useEffect(() => {
    const controller = new AbortController();
    const missingIds = [...new Set(applications.map((application) => application.teachingRequestId))].filter(
      (id) => !requestDetails[id] && !requestDetailErrors[id]
    );
    if (missingIds.length === 0) return () => controller.abort();

    Promise.allSettled(missingIds.map((id) => getTeachingRequest(id, controller.signal))).then((results) => {
      if (controller.signal.aborted) return;
      setRequestDetails((current) => {
        const next = { ...current };
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') next[missingIds[index]] = result.value;
        });
        return next;
      });
      setRequestDetailErrors((current) => {
        const next = { ...current };
        results.forEach((result, index) => {
          if (result.status === 'rejected') {
            next[missingIds[index]] = 'Không thể tải thông tin yêu cầu dạy để chỉnh sửa ứng tuyển.';
          }
        });
        return next;
      });
    });

    return () => controller.abort();
  }, [applications, requestDetails, requestDetailErrors]);

  const cancel = async (id: string) => {
    setCancelling(id);
    setError('');
    try {
      await cancelStudentApplication(id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Không thể hủy ứng tuyển này.');
    } finally {
      setCancelling(null);
    }
  };

  const saveEdit = async (values: StudentApplicationFormValues) => {
    if (!editingApplication) return;
    setSavingEdit(true);
    setEditError('');
    try {
      await updateStudentApplication(editingApplication, values);
      setEditingApplication(null);
      setRevision((value) => value + 1);
    } catch (caught) {
      setEditError(caught instanceof ApiError ? caught.message : 'Không thể cập nhật ứng tuyển này.');
    } finally {
      setSavingEdit(false);
    }
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <h1>Ứng tuyển của tôi</h1>
          <p>Theo dõi các yêu cầu bạn đã gửi đến gia sư.</p>
        </div>
        <Link className="tutor-button tutor-heading-button" to="/student/teaching-requests">
          Tìm yêu cầu dạy
        </Link>
      </div>

      {(error || query.error) && (
        <div className="tutor-error" role="alert">
          {error || query.error}
        </div>
      )}

      <ApplicationToolbar
        search={search}
        onSearchChange={(value) => {
          setSearch(value);
          setPage(0);
        }}
        placeholder="Tìm môn học, gia sư hoặc yêu cầu dạy"
        searchAriaLabel="Tìm ứng tuyển của tôi"
        filters={filters.map((item) => ({
          value: item,
          label: item === 'ALL' ? 'Tất cả' : applicationStatusLabel(item)
        }))}
        selectedFilter={filter}
        onFilterChange={(value) => {
          setFilter(value as 'ALL' | ApplicationStatus);
          setPage(0);
        }}
        filterAriaLabel="Lọc ứng tuyển"
      />

      {query.isLoading || (!pageData && !query.error) ? (
        <div className="tutor-empty">Đang tải danh sách ứng tuyển…</div>
      ) : applications.length ? (
        <div className="tutor-proposal-list">
          {applications.map((application) => (
            <StudentApplicationCard
              key={application.id}
              application={application}
              request={requestDetails[application.teachingRequestId]}
              cancelling={cancelling === application.id}
              onEdit={() => {
                setEditError('');
                setEditingApplication(application.id);
              }}
              onCancel={() => void cancel(application.id)}
            />
          ))}
        </div>
      ) : (
        <div className="tutor-empty">
          <span>↗</span>
          <h3>Chưa có ứng tuyển trong mục này</h3>
          <p>Hãy tìm một yêu cầu dạy phù hợp để gửi ứng tuyển đầu tiên của bạn.</p>
        </div>
      )}

      {!query.isLoading && pageData && pageData.totalPages > 1 && (
        <div className="tutor-marketplace-pagination" aria-label="Phân trang ứng tuyển">
          <button type="button" disabled={pageData.first} onClick={() => setPage((value) => value - 1)}>
            Trước
          </button>
          <span>
            Trang {pageData.page + 1}/{pageData.totalPages}
          </span>
          <button type="button" disabled={pageData.last} onClick={() => setPage((value) => value + 1)}>
            Sau
          </button>
        </div>
      )}

      {editingItem && (
        <StudentApplicationForm
          key={`edit-${editingApplication}`}
          application={editingItem}
          grades={requestDetails[editingItem.teachingRequestId]?.grades ?? null}
          gradeLoadError={requestDetailErrors[editingItem.teachingRequestId]}
          saving={savingEdit}
          error={editError}
          onClose={() => {
            setEditingApplication(null);
            setEditError('');
          }}
          onSubmit={(values) => void saveEdit(values)}
        />
      )}
    </>
  );
}
