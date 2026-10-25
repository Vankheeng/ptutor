import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { cancelTutorProposal, updateTutorProposal, type TutorTeachingProposal } from '../../services/tutorApi';
import { getStudyingRequest, type StudyingRequestSearchResponse } from '../../services/studyingRequestApi';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';
import { TutorProposalCard } from './components/TutorProposalCard';
import { TutorApplicationForm, type TutorApplicationValues } from './components/TutorApplicationForm';

const filters = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const;
type ProposalFilter = (typeof filters)[number];

export function TutorProposalsPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<ProposalFilter>('ALL');
  const [search, setSearch] = useState('');
  const [revision, setRevision] = useState(0);
  const proposalQuery = useTutorProposals(filter === 'ALL' ? undefined : filter, revision);
  const proposals = useMemo(() => proposalQuery.data ?? [], [proposalQuery.data]);
  const [requestDetails, setRequestDetails] = useState<Record<string, StudyingRequestSearchResponse>>({});
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [editingProposal, setEditingProposal] = useState<TutorTeachingProposal | null>(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    const missingIds = [...new Set(proposals.map((proposal) => proposal.studyingRequestId))].filter(
      (id) => !requestDetails[id]
    );
    if (missingIds.length === 0) return () => controller.abort();
    Promise.allSettled(missingIds.map((id) => getStudyingRequest(id, controller.signal))).then((results) => {
      if (controller.signal.aborted) return;
      setRequestDetails((current) => {
        const next = { ...current };
        results.forEach((result, index) => {
          if (result.status === 'fulfilled') next[missingIds[index]] = result.value;
        });
        return next;
      });
    });
    return () => controller.abort();
  }, [proposals, requestDetails]);

  const visibleProposals = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    if (!term) return proposals;
    return proposals.filter((proposal) => {
      const request = requestDetails[proposal.studyingRequestId];
      return [
        proposal.subjectName,
        proposal.gradeName,
        proposal.studyingRequestTitle,
        proposal.message,
        proposal.preferredSchedule,
        proposal.preferredDistrictName,
        proposal.preferredProvinceName,
        proposal.teachingMode,
        request?.districtName,
        request?.title,
        request?.preferredSchedule,
        request?.description,
        proposal.proposedPrice.toString()
      ].some((value) => value?.toLocaleLowerCase().includes(term));
    });
  }, [proposals, requestDetails, search]);

  const cancel = async (proposal: TutorTeachingProposal) => {
    setCancelling(proposal.id);
    setError('');
    try {
      await cancelTutorProposal(proposal.id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể hủy đề xuất này.');
    } finally {
      setCancelling(null);
    }
  };

  const saveEdit = async (values: TutorApplicationValues) => {
    if (!editingProposal) return;
    const request = requestDetails[editingProposal.studyingRequestId];
    if (!request) {
      setEditError('Đang tải chi tiết yêu cầu học. Vui lòng thử lại.');
      return;
    }
    setSavingEdit(true);
    setEditError('');
    try {
      await updateTutorProposal(editingProposal.id, { ...values, gradeId: request.gradeId });
      setEditingProposal(null);
      setRevision((value) => value + 1);
    } catch (caught) {
      setEditError(caught instanceof Error ? caught.message : 'Không thể cập nhật đề xuất.');
    } finally {
      setSavingEdit(false);
    }
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">HÀNH TRÌNH GIẢNG DẠY</span>
          <h1>Đề xuất giảng dạy của tôi</h1>
          <p>Theo dõi yêu cầu học và đề xuất bạn đã gửi cho học viên.</p>
        </div>
        <Link className="tutor-button tutor-heading-button" to="/tutor/dashboard">
          Tìm yêu cầu học
        </Link>
      </div>
      {(error || proposalQuery.error) && (
        <div className="tutor-error" role="alert">
          {error || proposalQuery.error}
        </div>
      )}
      <div className="tutor-proposal-controls">
        <label className="tutor-proposal-search">
          <span aria-hidden="true">⌕</span>
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm môn học, lịch dạy, giá hoặc địa chỉ"
            aria-label="Tìm đề xuất của tôi"
          />
        </label>
        <div className="tutor-filter-tabs" role="tablist" aria-label="Lọc đề xuất">
          {filters.map((item) => (
            <button
              className={filter === item ? 'active' : ''}
              type="button"
              role="tab"
              aria-selected={filter === item}
              key={item}
              onClick={() => setFilter(item)}
            >
              {item === 'ALL' ? 'Tất cả' : label(item)}
            </button>
          ))}
        </div>
      </div>
      {proposalQuery.isLoading ? (
        <div className="tutor-empty">Đang tải đề xuất…</div>
      ) : visibleProposals.length === 0 ? (
        <div className="tutor-empty">
          <span>↗</span>
          <h3>{search ? 'Không tìm thấy đề xuất phù hợp' : 'Chưa có đề xuất trong mục này'}</h3>
          <p>
            {search
              ? 'Thử từ khóa hoặc trạng thái khác.'
              : 'Tìm yêu cầu học đang mở và gửi đề xuất giảng dạy đầu tiên của bạn.'}
          </p>
        </div>
      ) : (
        <div className="tutor-proposal-list">
          {visibleProposals.map((proposal) => (
            <TutorProposalCard
              key={proposal.id}
              proposal={proposal}
              request={requestDetails[proposal.studyingRequestId]}
              cancelling={cancelling === proposal.id}
              onCancel={() => void cancel(proposal)}
              onOpenRequest={() => navigate(`/tutor/studying-requests/${proposal.studyingRequestId}`)}
              onEdit={() => {
                setEditError('');
                setEditingProposal(proposal);
              }}
            />
          ))}
        </div>
      )}
      {editingProposal && (
        <TutorApplicationForm
          key={`edit-${editingProposal.id}`}
          subjectName={requestDetails[editingProposal.studyingRequestId]?.subjectName ?? editingProposal.subjectName}
          requestMode={requestDetails[editingProposal.studyingRequestId]?.learningMode ?? editingProposal.teachingMode}
          proposal={editingProposal}
          editing
          sending={savingEdit}
          error={editError}
          onClose={() => {
            setEditingProposal(null);
            setEditError('');
          }}
          onSubmit={(values) => void saveEdit(values)}
        />
      )}
    </>
  );
}

function label(value: string) {
  const labels: Record<string, string> = {
    PENDING: 'Đang chờ',
    ACCEPTED: 'Đã chấp nhận',
    REJECTED: 'Bị từ chối',
    CANCELLED: 'Đã hủy'
  };
  return labels[value] ?? value;
}
