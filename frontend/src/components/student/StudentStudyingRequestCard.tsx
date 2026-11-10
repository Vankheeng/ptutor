import type { Ref } from 'react';
import type { StudyingRequest } from '../../services/studentApi';
import { formatStudyingRequestDate, statusLabel } from './studyingRequestUtils';
import { StudentStudyingRequestMenu, type StudyingRequestAction } from './StudentStudyingRequestMenu';

interface Props {
  request: StudyingRequest;
  menuOpen: boolean;
  busy: boolean;
  onOpen: (requestId: string) => void;
  onToggleMenu: (requestId: string) => void;
  onAction: (id: string, action: StudyingRequestAction) => void;
  onNavigate: (path: string) => void;
  menuRef?: Ref<HTMLDivElement>;
}

export function StudentStudyingRequestCard({
  request,
  menuOpen,
  busy,
  onOpen,
  onToggleMenu,
  onAction,
  onNavigate,
  menuRef
}: Props) {
  const normalizedStatus = request.status.trim().toUpperCase() as StudyingRequest['status'];

  return (
    <article
      className={`student-panel student-request-card status-${normalizedStatus.toLowerCase()}`}
      onClick={() => onOpen(request.id)}
      onKeyDown={(event) => {
        if (event.key === 'Enter') onOpen(request.id);
      }}
      tabIndex={0}
      role="link"
    >
      <div className="student-card-top">
        <span className="student-tag">{request.subjectName || 'Môn khác'}</span>
        <div className="student-card-menu" ref={menuRef}>
          <span className={`student-status ${normalizedStatus.toLowerCase()}`}>{statusLabel(normalizedStatus)}</span>
          <button
            type="button"
            aria-label="Thao tác với tin"
            aria-expanded={menuOpen}
            onClick={(event) => {
              event.stopPropagation();
              onToggleMenu(request.id);
            }}
          >
            ⋯
          </button>
          {menuOpen && (
            <StudentStudyingRequestMenu
              status={normalizedStatus}
              requestId={request.id}
              busy={busy}
              onAction={onAction}
              onNavigate={onNavigate}
            />
          )}
        </div>
      </div>
      <h3>{request.title}</h3>
      <p>{request.description || request.learningGoals || request.note || 'Chưa có mô tả.'}</p>
      <div className="student-card-actions">
        <span>
          {request.gradeName} · {request.learningMode === 'ONLINE' ? 'Online' : 'Trực tiếp'}
        </span>
        <span className="student-card-created">Tạo lúc {formatStudyingRequestDate(request.createdAt)}</span>
      </div>
    </article>
  );
}
