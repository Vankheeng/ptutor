import './TeachingPostCard.css';
import type { TeachingRequest } from '../../types/api';
import { formatCurrency, getInitials } from '../../utils/formatters';
import { Icon } from '../ui/Icon';

interface TeachingPostCardProps {
  post: TeachingRequest;
  index: number;
}

const accentTones = ['violet', 'blue', 'rose'];

export function TeachingPostCard({ post, index }: TeachingPostCardProps) {
  const subjectName = post.subjectName || post.customSubjectName || 'Môn học đang cập nhật';
  const gradeLabel =
    post.grades
      .map((grade) => grade.name)
      .slice(0, 2)
      .join(' · ') || 'Trình độ linh hoạt';
  const mode = post.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp';
  const schedule = post.preferredSchedule || 'Lịch trao đổi cùng gia sư';
  const description = post.description || post.note || 'Xem chi tiết để trao đổi mục tiêu và lộ trình học phù hợp.';

  return (
    <article className={`teaching-request-card request-tone-${accentTones[index % accentTones.length]}`}>
      <header className="request-card-header">
        <div>
          <p className="request-subject">{subjectName}</p>
          <span className="request-grade">{gradeLabel}</span>
        </div>
        <span className="request-mode">{mode}</span>
      </header>
      <div className="request-card-body">
        <div className="request-tutor">
          {post.tutorAvatarUrl ? (
            <img src={post.tutorAvatarUrl} alt={`Gia sư ${post.tutorName}`} />
          ) : (
            <span>{getInitials(post.tutorName)}</span>
          )}
          <div>
            <small>Gia sư</small>
            <strong>{post.tutorName || 'Gia sư Ptutor'}</strong>
          </div>
        </div>
        <h3>{post.title || `Lớp ${subjectName}`}</h3>
        <p className="request-description">{description}</p>
        <dl className="request-details">
          <div>
            <dt>Lịch học</dt>
            <dd>{schedule}</dd>
          </div>
          <div>
            <dt>Số học viên</dt>
            <dd>{post.quantity ? `${post.quantity} học viên` : 'Trao đổi thêm'}</dd>
          </div>
        </dl>
        <footer className="request-card-footer">
          <div>
            <small>Học phí dự kiến</small>
            <strong>
              {formatCurrency(post.expectedPrice)}
              <em>/ buổi</em>
            </strong>
          </div>
          <a href={`#teaching-request-${post.id}`}>
            Xem lớp <Icon name="arrow" size={16} />
          </a>
        </footer>
      </div>
    </article>
  );
}
