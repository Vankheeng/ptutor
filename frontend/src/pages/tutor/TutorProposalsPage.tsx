import { useState } from 'react';
import { Link } from 'react-router-dom';
import { cancelTutorProposal, type TutorTeachingProposal } from '../../services/tutorApi';
import { useTutorProposals } from '../../hooks/useTutorWorkspace';

const filters = ['ALL', 'PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'] as const;
type ProposalFilter = (typeof filters)[number];

export function TutorProposalsPage() {
  const [filter, setFilter] = useState<ProposalFilter>('ALL');
  const [revision, setRevision] = useState(0);
  const proposalQuery = useTutorProposals(filter === 'ALL' ? undefined : filter, revision);
  const proposals = proposalQuery.data ?? [];
  const [error, setError] = useState('');
  const loading = proposalQuery.isLoading;
  const [cancelling, setCancelling] = useState<string | null>(null);

  const cancel = async (proposal: TutorTeachingProposal) => {
    setCancelling(proposal.id);
    setError('');
    try {
      await cancelTutorProposal(proposal.id);
      setRevision((value) => value + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Could not cancel this proposal.');
    } finally {
      setCancelling(null);
    }
  };

  return (
    <>
      <div className="tutor-page-heading">
        <div>
          <span className="tutor-eyebrow">YOUR TEACHING PIPELINE</span>
          <h1>My requests to teach</h1>
          <p>Track the proposals you have sent to students.</p>
        </div>
        <Link className="tutor-button tutor-heading-button" to="/tutor/dashboard">
          Browse requests
        </Link>
      </div>
      {(error || proposalQuery.error) && (
        <div className="tutor-error" role="alert">
          {error || proposalQuery.error}
        </div>
      )}
      <div className="tutor-filter-tabs" role="tablist" aria-label="Filter proposals">
        {filters.map((item) => (
          <button
            className={filter === item ? 'active' : ''}
            type="button"
            role="tab"
            aria-selected={filter === item}
            key={item}
            onClick={() => setFilter(item)}
          >
            {item === 'ALL' ? 'All' : label(item)}
          </button>
        ))}
      </div>
      {loading ? (
        <div className="tutor-empty">Loading proposals…</div>
      ) : proposals.length === 0 ? (
        <div className="tutor-empty">
          <span>↗</span>
          <h3>No proposals in this view</h3>
          <p>Browse open student requests and send your first teaching proposal.</p>
        </div>
      ) : (
        <div className="tutor-proposal-list">
          {proposals.map((proposal) => (
            <article className="tutor-panel tutor-proposal-card" key={proposal.id}>
              <div className="tutor-cert-title">
                <div>
                  <span className="tutor-eyebrow">
                    {proposal.subjectName} · {proposal.gradeName}
                  </span>
                  <h2>{proposal.studyingRequestTitle || `${proposal.subjectName} learning request`}</h2>
                </div>
                <span className={`tutor-cert-status status-${proposal.status.toLowerCase()}`}>
                  {label(proposal.status)}
                </span>
              </div>
              <p>{proposal.message || 'No message included.'}</p>
              <div className="tutor-proposal-summary">
                <span>
                  <small>Proposed price</small>
                  <strong>{new Intl.NumberFormat('vi-VN').format(proposal.proposedPrice)} VND</strong>
                </span>
                <span>
                  <small>Mode</small>
                  <strong>{proposal.teachingMode === 'ONLINE' ? 'Online' : 'In person'}</strong>
                </span>
                <span>
                  <small>Schedule</small>
                  <strong>{proposal.preferredSchedule || 'Flexible'}</strong>
                </span>
              </div>
              <div className="tutor-proposal-footer">
                <small>{proposal.nextStep || `Sent ${new Date(proposal.createdAt).toLocaleDateString()}`}</small>
                <div>
                  <Link className="tutor-detail-link" to={`/tutor/studying-requests/${proposal.studyingRequestId}`}>
                    Open request ↗
                  </Link>
                  {proposal.status === 'PENDING' && (
                    <button
                      className="tutor-button secondary"
                      type="button"
                      disabled={cancelling === proposal.id}
                      onClick={() => void cancel(proposal)}
                    >
                      {cancelling === proposal.id ? 'Cancelling…' : 'Cancel proposal'}
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}

function label(value: string) {
  return value.toLowerCase().replaceAll('_', ' ');
}
