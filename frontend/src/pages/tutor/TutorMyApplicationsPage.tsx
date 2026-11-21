import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  cancelTutorProposal,
  getTutorContracts,
  updateTutorProposal,
  type TutorTeachingProposal
} from '../../services/tutorApi';
import { getStudyingRequest, type StudyingRequestSearchResponse } from '../../services/studyingRequestApi';
import { useTutorProposalsPage } from '../../hooks/useTutorWorkspace';
import { ApplicationToolbar } from '../../components/applications/ApplicationToolbar';
import { TutorProposalCard } from './components/TutorProposalCard';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';

const filters = ['ALL', 'PENDING', 'ACCEPTED', 'CONNECTED', 'REJECTED', 'CANCELLED'] as const;
type ProposalFilter = (typeof filters)[number];

export function TutorMyApplicationsPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<ProposalFilter>('ALL');
  const [search, setSearch] = useState('');
  const [requestStatus, setRequestStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [details, setDetails] = useState<Record<string, StudyingRequestSearchResponse>>({});
  const [contractIds, setContractIds] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<TutorTeachingProposal | null>(null);
  const [busy, setBusy] = useState(false);
  const query = useTutorProposalsPage(filter === 'ALL' ? undefined : filter, page, revision);
  const proposals = useMemo(() => query.data?.content ?? [], [query.data]);

  useEffect(() => {
    const controller = new AbortController();
    getTutorContracts(controller.signal)
      .then((page) => {
        if (!controller.signal.aborted) {
          setContractIds(
            Object.fromEntries(
              page.content
                .filter((contract) => contract.tutorStudentRequestId)
                .map((contract) => [contract.tutorStudentRequestId as string, contract.id])
            )
          );
        }
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [revision]);

  useEffect(() => {
    const controller = new AbortController();
    const ids = [...new Set(proposals.map((item) => item.studyingRequestId))].filter((id) => !details[id]);
    if (!ids.length) return () => controller.abort();
    Promise.all(ids.map(async (id) => [id, await getStudyingRequest(id, controller.signal)] as const))
      .then((items) => {
        if (!controller.signal.aborted) setDetails((current) => ({ ...current, ...Object.fromEntries(items) }));
      })
      .catch(() => undefined);
    return () => controller.abort();
  }, [details, proposals]);

  const visible = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('vi-VN');
    return proposals.filter((item) => {
      const request = details[item.studyingRequestId];
      if (requestStatus !== 'ALL' && request?.status !== requestStatus) return false;
      if (requestStatus !== 'ALL' && !request) return false;
      if (!term) return true;
      return [
        item.subjectName,
        item.gradeName,
        item.studyingRequestTitle,
        item.message,
        item.preferredSchedule,
        item.proposedPrice.toString()
      ].some((value) => value?.toLocaleLowerCase('vi-VN').includes(term));
    });
  }, [details, proposals, requestStatus, search]);

  const cancel = async (item: TutorTeachingProposal) => {
    setBusy(true);
    try {
      await cancelTutorProposal(item.id);
      setRevision((value) => value + 1);
    } finally {
      setBusy(false);
    }
  };
  const save = async (values: TutorApplicationValues) => {
    if (!selected) return;
    const request = details[selected.studyingRequestId];
    if (!request) return;
    setBusy(true);
    try {
      await updateTutorProposal(selected.id, { ...values, gradeId: request.gradeId });
      setSelected(null);
      setRevision((value) => value + 1);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <h1>Ứng tuyển của tôi</h1>
        </div>
        <Link className="tutor-button tutor-heading-button" to="/tutor/students">
          Tìm học viên
        </Link>
      </div>
      {query.error && (
        <div className="tutor-error" role="alert">
          {query.error}
        </div>
      )}
      <ApplicationToolbar
        search={search}
        onSearchChange={setSearch}
        placeholder="Tìm môn học, lịch dạy, giá hoặc địa chỉ"
        searchAriaLabel="Tìm ứng tuyển của tôi"
        filters={filters.map((item) => ({ value: item, label: item === 'ALL' ? 'Tất cả' : label(item) }))}
        selectedFilter={filter}
        onFilterChange={(value) => {
          setFilter(value as ProposalFilter);
          setPage(0);
        }}
        filterAriaLabel="Lọc ứng tuyển"
        filterLabel="Trạng thái ứng tuyển"
        secondaryFilter={{
          value: requestStatus,
          onChange: (value) => {
            setRequestStatus(value);
            setPage(0);
          },
          ariaLabel: 'Lọc trạng thái yêu cầu học',
          label: 'Trạng thái yêu cầu học',
          options: [
            { value: 'ALL', label: 'Tất cả trạng thái yêu cầu' },
            { value: 'DRAFT', label: 'Yêu cầu bản nháp' },
            { value: 'PENDING_REVIEW', label: 'Yêu cầu chờ duyệt' },
            { value: 'OPEN', label: 'Yêu cầu đang mở' },
            { value: 'MATCHED', label: 'Yêu cầu đã kết nối' },
            { value: 'CLOSED', label: 'Yêu cầu đã đóng' },
            { value: 'CANCELLED', label: 'Yêu cầu đã hủy' },
            { value: 'REJECTED', label: 'Yêu cầu bị từ chối' }
          ]
        }}
      />
      {query.isLoading ? (
        <div className="tutor-empty">Đang tải ứng tuyển...</div>
      ) : !visible.length ? (
        <div className="tutor-empty">
          <h3>Chưa có ứng tuyển phù hợp</h3>
          <p>Hãy tìm yêu cầu học đang mở và gửi đề xuất giảng dạy.</p>
        </div>
      ) : (
        <div className="tutor-proposal-list">
          {visible.map((item) => (
            <TutorProposalCard
              key={item.id}
              proposal={item}
              request={details[item.studyingRequestId]}
              contractId={contractIds[item.id]}
              cancelling={busy}
              onCancel={() => void cancel(item)}
              onOpenRequest={() => navigate(`/tutor/studying-requests/${item.studyingRequestId}`)}
              onEdit={() => setSelected(item)}
            />
          ))}
        </div>
      )}
      {!query.isLoading && query.data && query.data.totalPages > 1 && (
        <div className="tutor-marketplace-pagination" aria-label="Phân trang ứng tuyển của tôi">
          <button type="button" disabled={query.data.first} onClick={() => setPage((value) => value - 1)}>
            Trước
          </button>
          <span>
            Trang {query.data.page + 1}/{query.data.totalPages}
          </span>
          <button type="button" disabled={query.data.last} onClick={() => setPage((value) => value + 1)}>
            Sau
          </button>
        </div>
      )}
      {selected && (
        <TutorApplicationForm
          key={selected.id}
          subjectName={details[selected.studyingRequestId]?.subjectName ?? selected.subjectName}
          requestMode={details[selected.studyingRequestId]?.learningMode ?? selected.teachingMode}
          requestProvinceId={details[selected.studyingRequestId]?.provinceId}
          requestDistrictId={details[selected.studyingRequestId]?.districtId}
          proposal={selected}
          editing
          sending={busy}
          error=""
          onClose={() => setSelected(null)}
          onSubmit={(values) => void save(values)}
        />
      )}
    </>
  );
}

function label(value: string) {
  return (
    (
      {
        PENDING: 'Đang chờ',
        ACCEPTED: 'Đã chấp nhận',
        CONNECTED: 'Đã kết nối',
        REJECTED: 'Bị từ chối',
        CANCELLED: 'Đã hủy'
      } as Record<string, string>
    )[value] ?? value
  );
}
