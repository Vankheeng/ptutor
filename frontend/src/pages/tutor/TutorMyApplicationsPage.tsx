import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { cancelTutorProposal, updateTutorProposal, type TutorTeachingProposal } from '../../services/tutorApi';
import { getStudyingRequest, type StudyingRequestSearchResponse } from '../../services/studyingRequestApi';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { ApplicationToolbar } from '../../components/applications/ApplicationToolbar';
import { TutorProposalCard } from './components/TutorProposalCard';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';

const filters = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const;
type ProposalFilter = (typeof filters)[number];

export function TutorMyApplicationsPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<ProposalFilter>('ALL');
  const [search, setSearch] = useState('');
  const [revision, setRevision] = useState(0);
  const [details, setDetails] = useState<Record<string, StudyingRequestSearchResponse>>({});
  const [selected, setSelected] = useState<TutorTeachingProposal | null>(null);
  const [busy, setBusy] = useState(false);
  const query = useTutorProposals(filter === 'ALL' ? undefined : filter, revision);
  const proposals = query.data ?? [];

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
    if (!term) return proposals;
    return proposals.filter((item) =>
      [
        item.subjectName,
        item.gradeName,
        item.studyingRequestTitle,
        item.message,
        item.preferredSchedule,
        item.proposedPrice.toString()
      ].some((value) => value?.toLocaleLowerCase('vi-VN').includes(term))
    );
  }, [proposals, search]);

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
          <p>Theo dõi các đề xuất giảng dạy bạn đã gửi cho học viên.</p>
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
        onFilterChange={(value) => setFilter(value as ProposalFilter)}
        filterAriaLabel="Lọc ứng tuyển"
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
              cancelling={busy}
              onCancel={() => void cancel(item)}
              onOpenRequest={() => navigate(`/tutor/studying-requests/${item.studyingRequestId}`)}
              onEdit={() => setSelected(item)}
            />
          ))}
        </div>
      )}
      {selected && (
        <TutorApplicationForm
          key={selected.id}
          subjectName={details[selected.studyingRequestId]?.subjectName ?? selected.subjectName}
          requestMode={details[selected.studyingRequestId]?.learningMode ?? selected.teachingMode}
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
      { PENDING: 'Đang chờ', ACCEPTED: 'Đã chấp nhận', REJECTED: 'Bị từ chối', CANCELLED: 'Đã hủy' } as Record<
        string,
        string
      >
    )[value] ?? value
  );
}
