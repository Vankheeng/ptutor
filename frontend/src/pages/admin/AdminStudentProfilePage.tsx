import { actorName, daysLeft, reactivationReasonLabel } from './accountFormat';
import { formatDate, formatDateTime, genderLabels, splitTags } from './adminFormat';
import { useAdminStudent } from './adminStudentContext';
import { DefinitionGrid } from './components/AdminUi';

export function AdminStudentProfilePage() {
  const { account, profile } = useAdminStudent();
  const blocked = account.status === 'BLOCKED';
  const temporary = account.suspensionType === 'TEMPORARY';
  const remaining = temporary ? daysLeft(account.suspendedUntil) : null;
  const personality = splitTags(profile.personalityTags);

  return (
    <div className="admin-columns">
      <div className="admin-column-main">
        <section className="admin-card admin-card-pad">
          <h2>Thông tin cá nhân</h2>
          <DefinitionGrid
            columns={3}
            items={[
              ['Họ', profile.firstName || '—'],
              ['Tên', profile.lastName || '—'],
              ['Giới tính', profile.gender ? genderLabels[profile.gender] : '—'],
              ['Ngày sinh', formatDate(profile.dateOfBirth)],
              ['Số điện thoại', profile.phone || '—'],
              [
                'Địa chỉ',
                [profile.address?.detailAddress, profile.address?.districtName, profile.address?.provinceName]
                  .filter(Boolean)
                  .join(', ') || '—'
              ]
            ]}
          />
        </section>
        <section className="admin-card admin-card-pad">
          <h2>Giới thiệu</h2>
          <p className="admin-prose">{profile.introduction || 'Chưa có giới thiệu.'}</p>
        </section>
        <section className="admin-card admin-card-pad">
          <h2>Hồ sơ học tập</h2>
          {personality.length > 0 && (
            <div className="admin-tags">
              {personality.map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
          )}
          <DefinitionGrid
            items={[
              ['Trình độ hiện tại', profile.currentLevel || '—'],
              ['Phong cách học', profile.learningStyle || '—'],
              ['Mục tiêu', profile.goalsDescription || '—'],
              ['Điểm yếu', profile.weakPoints || '—']
            ]}
          />
        </section>
      </div>
      <div className="admin-column-side">
        {blocked && (
          <section
            className={`admin-card admin-card-pad admin-suspension admin-suspension-${temporary ? 'temporary' : 'permanent'}`}
          >
            <div className="admin-section-heading">
              <h2>{temporary ? 'Đang khóa tạm thời' : 'Đang khóa vĩnh viễn'}</h2>
              {remaining !== null && <span>Còn {remaining} ngày</span>}
            </div>
            <DefinitionGrid
              items={[
                ['Khóa lúc', formatDateTime(account.suspendedAt)],
                ['Khóa đến', temporary ? formatDateTime(account.suspendedUntil) : 'Không thời hạn'],
                ['Người thực hiện', account.suspendedByUserId ? actorName(account.suspendedByName) : '—']
              ]}
            />
            <div className="admin-stack">
              <strong>Lý do</strong>
              <p className="admin-prose">{account.suspensionReason ?? '—'}</p>
            </div>
          </section>
        )}
        <section className="admin-card admin-card-pad">
          <h2>Tài khoản</h2>
          <DefinitionGrid
            items={[
              ['Email', account.email],
              ['Số lần bị khóa', account.suspensionCount],
              ['Ngày tạo', formatDateTime(account.createdAt)],
              ['Cập nhật lần cuối', formatDateTime(account.updatedAt)]
            ]}
          />
        </section>
        {account.reactivatedAt && (
          <section className="admin-card admin-card-pad">
            <h2>Lần mở khóa gần nhất</h2>
            <DefinitionGrid
              items={[
                ['Mở khóa lúc', formatDateTime(account.reactivatedAt)],
                [
                  'Người thực hiện',
                  account.reactivatedByUserId ? actorName(account.reactivatedByName) : 'Tự động khi hết hạn'
                ]
              ]}
            />
            {account.reactivationReason && (
              <div className="admin-stack">
                <strong>Lý do</strong>
                <p className="admin-prose">{reactivationReasonLabel(account.reactivationReason)}</p>
              </div>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
