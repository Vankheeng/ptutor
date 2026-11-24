import type { Contract } from '../../../services/studentApi';
import type { StudentComplaint } from '../../../services/studentComplaintApi';
import { Link } from 'react-router-dom';
import { complaintCategoryLabels, formatComplaintDate } from './complaintFormat';
import { Icon } from '../../ui/Icon';
import { useComplaintParties } from '../../../pages/common-user/useComplaintParties';

interface Props {
  complaint: StudentComplaint;
  contract: Contract | null;
  mode: 'submitted' | 'received';
  role?: 'student' | 'tutor';
  contractHref: string;
  variant?: 'default' | 'redesigned';
}

export function ComplaintDetailPanel({
  complaint,
  contract,
  mode,
  role = 'student',
  contractHref,
  variant = 'default'
}: Props) {
  const { senderName, respondentName, respondentRole } = useComplaintParties(contract, mode, role);

  if (variant === 'redesigned') {
    const targetIsTutor = (role === 'student' && mode === 'submitted') || (role === 'tutor' && mode === 'received');
    const targetName = contract
      ? targetIsTutor
        ? [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || contract.tutorEmail
        : [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || contract.studentEmail
      : 'Chưa cập nhật';
    const targetRole = targetIsTutor ? 'Gia sư' : 'Học viên';
    const contractCode = `HD-${complaint.contractId.slice(0, 8).toUpperCase()}`;

    return (
      <div className="student-complaint-detail-main-stack student-complaint-detail-main-stack--new">
        <section className="student-panel student-complaint-new-info">
          <h2 className="student-complaint-card-title">
            <Icon name="complaint" size={19} /> Thông tin khiếu nại
          </h2>
          <div className="student-complaint-new-info-body">
            <div className="student-complaint-new-info-icon" aria-hidden="true">
              <Icon name="complaint" size={31} />
            </div>
            <div className="student-complaint-new-info-copy">
              <span className="student-complaint-category-pill">{complaintCategoryLabels[complaint.category]}</span>
              <h3>{complaint.title}</h3>
              <div className="student-complaint-new-info-meta">
                <Link to={contractHref}>
                  <Icon name="contract" size={14} /> Hợp đồng <strong>{contractCode}</strong>
                </Link>
                <span>
                  <Icon name="calendar" size={14} /> Ngày tạo{' '}
                  <strong>{formatComplaintDate(complaint.createdAt)}</strong>
                </span>
              </div>
            </div>
          </div>
        </section>

        <section className="student-panel student-complaint-new-target">
          <h2 className="student-complaint-card-title">
            <Icon name="user" size={19} /> Đối tượng khiếu nại
          </h2>
          <div className="student-complaint-new-target-body">
            <span className="student-complaint-new-avatar" aria-hidden="true">
              {getInitials(targetName)}
            </span>
            <div>
              <strong>{targetName}</strong>
              <small>{targetRole}</small>
            </div>
          </div>
        </section>

        <section className="student-panel student-complaint-content-card">
          <h2 className="student-complaint-card-title">
            <Icon name="complaint" size={19} /> Nội dung khiếu nại
          </h2>
          <div className="student-complaint-content-box">{complaint.content}</div>
        </section>

        <section className="student-panel student-complaint-evidence-card student-complaint-new-evidence">
          <h2 className="student-complaint-card-title">
            <Icon name="send" size={19} /> Minh chứng
          </h2>
          {complaint.evidences.length > 0 ? (
            <div className="student-complaint-new-evidence-list">
              {complaint.evidences.map((evidence, index) => {
                const fileName = evidenceName(evidence.fileUrl, index);
                return (
                  <div
                    className="student-complaint-new-evidence-item"
                    key={evidence.id ?? `${evidence.fileUrl}-${index}`}
                  >
                    <a
                      className="student-complaint-new-evidence-preview"
                      href={evidence.fileUrl}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {evidence.fileType.startsWith('image/') ? (
                        <img src={evidence.fileUrl} alt={`Minh chứng ${index + 1}`} />
                      ) : (
                        <Icon name="contract" size={23} />
                      )}
                    </a>
                    <div>
                      <strong>{fileName}</strong>
                      <small>{evidence.fileType || 'Tệp minh chứng'}</small>
                    </div>
                    <a
                      className="student-complaint-evidence-download"
                      href={evidence.fileUrl}
                      download={fileName}
                      target="_blank"
                      rel="noreferrer"
                    >
                      Tải xuống
                    </a>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="student-complaint-empty-detail">Chưa có minh chứng cho khiếu nại này.</p>
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
                    {respondentName} ({respondentRole})
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
              <Link className="student-complaint-contract-link" to={contractHref}>
                <Icon name="contract" size={14} /> Chi tiết hợp đồng <Icon name="arrow" size={13} />
              </Link>
            </div>
          </div>
          <div className="student-complaint-contract-summary">
            <Link className="student-complaint-contract-summary-link" to={contractHref}>
              Xem chi tiết hợp đồng <Icon name="arrow" size={13} />
            </Link>
          </div>
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
              {respondentName} ({respondentRole})
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
                  {respondentName} ({respondentRole})
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

function evidenceName(fileUrl: string, index: number) {
  try {
    const pathname = new URL(fileUrl, window.location.origin).pathname;
    return decodeURIComponent(pathname.split('/').filter(Boolean).pop() || `minh-chung-${index + 1}`);
  } catch {
    return `minh-chung-${index + 1}`;
  }
}
