import { useCallback, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Icon, type IconName } from '../../components/ui/Icon';
import { useResource } from '../../hooks/useResource';
import { getStudentProfile, type StudentProfile } from '../../services/studentApi';
import { StudentProfilePage } from './StudentProfilePage';
import './student-profile.css';

const genderLabels: Record<NonNullable<StudentProfile['gender']>, string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác'
};

const accountStatus = {
  ACTIVE: {
    label: 'Hoạt động',
    description: 'Bạn có thể sử dụng đầy đủ các tính năng của hệ thống.',
    className: 'active'
  },
  INACTIVE: {
    label: 'Không hoạt động',
    description: 'Tài khoản hiện đang không hoạt động.',
    className: 'inactive'
  },
  BLOCKED: {
    label: 'Đã bị khóa',
    description: 'Tài khoản hiện đang bị hạn chế theo trạng thái hệ thống.',
    className: 'blocked'
  }
} satisfies Record<StudentProfile['status'], { label: string; description: string; className: string }>;

function display(value: string | null | undefined) {
  return value?.trim() || 'Chưa cập nhật';
}

function formatDate(value: string | null) {
  if (!value) return 'Chưa cập nhật';
  const date = new Date(`${value}T00:00:00`);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('vi-VN');
}

function splitTags(value: string | null) {
  return value
    ? value
        .split(/[,;|]/)
        .map((tag) => tag.trim())
        .filter(Boolean)
    : [];
}

function ProfileField({ icon, label, value }: { icon: IconName; label: string; value: string }) {
  return (
    <div className="student-profile-field">
      <Icon name={icon} size={17} />
      <span>
        <small>{label}</small>
        <strong>{value}</strong>
      </span>
    </div>
  );
}

function LearningField({ icon, label, value }: { icon: IconName; label: string; value: string }) {
  return (
    <div className="student-profile-learning-field">
      <Icon name={icon} size={19} />
      <span>
        <small>{label}</small>
        <strong>{value}</strong>
      </span>
    </div>
  );
}

function StudentProfileOverview({ profile, onEdit }: { profile: StudentProfile; onEdit: () => void }) {
  const address = [profile.address?.detailAddress, profile.address?.districtName, profile.address?.provinceName]
    .filter(Boolean)
    .join(', ');
  const tags = splitTags(profile.personalityTags);
  const status = accountStatus[profile.status];

  return (
    <div className="student-profile-overview">
      <div className="student-profile-overview-main">
        <section className="student-profile-card student-profile-personal-card">
          <header className="student-profile-card-heading">
            <h2>
              <Icon name="user" size={19} /> Thông tin cá nhân
            </h2>
            <button type="button" onClick={onEdit}>
              <Icon name="edit" size={14} /> Chỉnh sửa
            </button>
          </header>
          <div className="student-profile-personal-grid">
            <ProfileField icon="user" label="Họ" value={display(profile.firstName)} />
            <ProfileField icon="user" label="Tên" value={display(profile.lastName)} />
            <ProfileField
              icon="award"
              label="Giới tính"
              value={profile.gender ? genderLabels[profile.gender] : 'Chưa cập nhật'}
            />
            <ProfileField icon="calendar" label="Ngày sinh" value={formatDate(profile.dateOfBirth)} />
            <ProfileField icon="send" label="Email" value={profile.email} />
            <ProfileField icon="support" label="Số điện thoại" value={display(profile.phone)} />
            <div className="student-profile-field student-profile-address-field">
              <Icon name="location" size={17} />
              <span>
                <small>Địa chỉ</small>
                <strong>{address || 'Chưa cập nhật'}</strong>
              </span>
            </div>
          </div>
        </section>

        <section className="student-profile-card student-profile-introduction-card">
          <header className="student-profile-card-heading">
            <h2>
              <Icon name="award" size={19} /> Giới thiệu
            </h2>
          </header>
          <p>{profile.introduction?.trim() || 'Bạn chưa cập nhật phần giới thiệu.'}</p>
        </section>
      </div>

      <aside className="student-profile-card student-profile-status-card">
        <header className="student-profile-card-heading">
          <h2>
            <Icon name="shield" size={19} /> Trạng thái tài khoản
          </h2>
        </header>
        <div className={`student-profile-account-status ${status.className}`}>
          <Icon name="shield" size={31} />
          <strong>{status.label}</strong>
          <p>{status.description}</p>
        </div>
      </aside>

      <section className="student-profile-card student-profile-learning-card">
        <header className="student-profile-card-heading">
          <h2>
            <Icon name="book" size={19} /> Hồ sơ học tập
          </h2>
        </header>
        {tags.length > 0 && (
          <div className="student-profile-tags">
            {tags.map((tag) => (
              <span key={tag}>{tag}</span>
            ))}
          </div>
        )}
        <div className="student-profile-learning-grid">
          <LearningField icon="book" label="Trình độ hiện tại" value={display(profile.currentLevel)} />
          <LearningField icon="history" label="Phong cách học" value={display(profile.learningStyle)} />
          <LearningField icon="award" label="Mục tiêu" value={display(profile.goalsDescription)} />
          <LearningField icon="support" label="Nội dung cần hỗ trợ" value={display(profile.weakPoints)} />
        </div>
      </section>
    </div>
  );
}

export function StudentProfileWorkspacePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [revision, setRevision] = useState(0);
  const tab = searchParams.get('tab') === 'edit' ? 'edit' : 'overview';
  const load = useCallback((signal: AbortSignal) => getStudentProfile(signal), []);
  const { data: profile, error, isLoading } = useResource(load, revision);

  const fullName = useMemo(
    () => [profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || 'Học viên',
    [profile]
  );
  const location = useMemo(
    () => [profile?.address?.districtName, profile?.address?.provinceName].filter(Boolean).join(', '),
    [profile]
  );
  const initials = fullName
    .split(/\s+/)
    .filter(Boolean)
    .slice(-2)
    .map((part) => part.charAt(0).toLocaleUpperCase('vi-VN'))
    .join('');

  if (isLoading && !profile)
    return (
      <div className="student-empty">
        <h3>Đang tải hồ sơ…</h3>
      </div>
    );
  if (!profile)
    return (
      <div className="student-error" role="alert">
        {error || 'Không thể tải hồ sơ của bạn.'}
      </div>
    );

  return (
    <main className="student-profile-page">
      <header className="student-profile-hero">
        <div className="student-profile-identity">
          <div className="student-profile-avatar">
            {profile.avatarUrl ? <img src={profile.avatarUrl} alt="Ảnh đại diện" /> : initials}
          </div>
          <div>
            <span>HỒ SƠ HỌC VIÊN</span>
            <h1>{fullName}</h1>
            <p>
              <span>
                <Icon name="book" size={14} /> Học viên
              </span>
              <span>
                <Icon name="user" size={14} /> Hồ sơ của tôi
              </span>
              {location && (
                <span>
                  <Icon name="location" size={14} /> {location}
                </span>
              )}
            </p>
          </div>
        </div>
      </header>

      <nav className="student-profile-tabs" aria-label="Mục hồ sơ">
        <button type="button" className={tab === 'overview' ? 'active' : ''} onClick={() => setSearchParams({})}>
          <Icon name="user" size={17} /> Tổng quan
        </button>
        <button
          type="button"
          className={tab === 'edit' ? 'active' : ''}
          onClick={() => setSearchParams({ tab: 'edit' })}
        >
          <Icon name="edit" size={17} /> Chỉnh sửa hồ sơ
        </button>
      </nav>

      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      {tab === 'overview' ? (
        <StudentProfileOverview profile={profile} onEdit={() => setSearchParams({ tab: 'edit' })} />
      ) : (
        <div className="student-profile-edit-card">
          <StudentProfilePage onSaved={() => setRevision((value) => value + 1)} />
        </div>
      )}
    </main>
  );
}
