import type { Contract } from '../../../services/studentApi';
import type { StudentComplaint } from '../../../services/studentComplaintApi';
import { complaintCategoryLabels, formatComplaintDate } from './studentComplaintFormat';
import { StudentComplaintStatusBadge } from './StudentComplaintStatusBadge';
import { Icon } from '../../ui/Icon';

interface Props {
  complaint: StudentComplaint;
  contract: Contract | null;
  mode: 'submitted' | 'received';
}

export function StudentComplaintDetailPanel({ complaint, contract, mode }: Props) {
  const tutorName = contract
    ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || contract.tutorEmail
    : `#${complaint.contractId.slice(0, 8)}`;
  const studentName = contract
    ? [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || contract.studentEmail
    : `#${complaint.contractId.slice(0, 8)}`;
  const contractCode = `HD-${complaint.contractId.slice(0, 8).toUpperCase()}`;
  const senderName = mode === 'submitted' ? 'Bạn (Học viên)' : tutorName;
  const respondentName = mode === 'submitted' ? tutorName : studentName;

  return (
    <div className="student-complaint-detail-main-stack">
      <section className="student-panel student-complaint-summary-card">
        <div className="student-complaint-summary-main">
          <div className="student-complaint-summary-icon">
            <Icon name="complaint" size={25} />
          </div>
          <div className="student-complaint-summary-copy">
            <h2>{complaint.title}</h2>
            <div className="student-complaint-summary-tags">
              <span className="student-complaint-category-pill">{complaintCategoryLabels[complaint.category]}</span>
              <a className="student-complaint-contract-link" href={`/student/contracts/${complaint.contractId}`}>
                <Icon name="contract" size={14} /> {contractCode} <Icon name="arrow" size={13} />
              </a>
            </div>
          </div>
          <StudentComplaintStatusBadge status={complaint.status} />
        </div>
        <div className="student-complaint-summary-meta">
          <div>
            <span>
              <Icon name="user" size={15} /> Người gửi
            </span>
            <strong>{senderName}</strong>
          </div>
          <div>
            <span>
              <Icon name="user" size={15} /> Đối tượng bị khiếu nại
            </span>
            <strong>
              {respondentName} ({mode === 'submitted' ? 'Gia sư' : 'Học viên'})
            </strong>
          </div>
          <div>
            <span>
              <Icon name="calendar" size={15} /> Thời gian tạo
            </span>
            <strong>{formatComplaintDate(complaint.createdAt)}</strong>
          </div>
          <div>
            <span>
              <Icon name="history" size={15} /> Cập nhật gần nhất
            </span>
            <strong>{formatComplaintDate(complaint.updatedAt)}</strong>
          </div>
        </div>
      </section>

      <section className="student-panel student-complaint-content-card">
        <h2 className="student-complaint-card-title">
          <Icon name="complaint" size={19} /> Nội dung khiếu nại
        </h2>
        <div className="student-complaint-content-box">{complaint.content}</div>
      </section>

      <section className="student-panel student-complaint-evidence-card">
        <h2 className="student-complaint-card-title">
          <Icon name="send" size={19} /> Minh chứng ({complaint.evidences.length})
        </h2>
        {complaint.evidences.length > 0 ? (
          <div className="student-complaint-evidence-list">
            {complaint.evidences.map((evidence, index) => (
              <a
                key={evidence.id ?? `${evidence.fileUrl}-${index}`}
                href={evidence.fileUrl}
                target="_blank"
                rel="noreferrer"
              >
                <Icon name="arrow" size={13} /> <span>{evidence.fileUrl}</span>
                <Icon name="arrow" size={13} />
              </a>
            ))}
          </div>
        ) : (
          <p className="student-complaint-empty-detail">Chưa có minh chứng.</p>
        )}
      </section>

      {complaint.respondentResponse && (
        <section className="student-panel student-complaint-message student-complaint-response-message">
          <h2 className="student-complaint-card-title">
            <Icon name="support" size={19} /> Phản hồi của bên bị khiếu nại
          </h2>
          <div className="student-complaint-response-body">
            <div className="student-complaint-response-author">
              <span className="student-complaint-response-avatar" aria-hidden="true">
                {getInitials(respondentName)}
              </span>
              <div>
                <strong>
                  {respondentName} ({mode === 'submitted' ? 'Gia sư' : 'Học viên'})
                </strong>
                <small>Phản hồi lúc: {formatComplaintDate(complaint.respondentRespondedAt)}</small>
              </div>
            </div>
            <p>{complaint.respondentResponse}</p>
          </div>
        </section>
      )}
    </div>
  );
}

function getInitials(name: string) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase())
    .join('');
  return initials.slice(0, 2) || '?';
}
