import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/useAuth';
import { useResource } from '../../hooks/useResource';
import { certificateStatusMeta, profileStatusMeta, splitTags } from '../admin/adminFormat';
import { DefinitionGrid, StatusBadge } from '../admin/components/AdminUi';
import {
  getPublicStudentProfile,
  getSharedContracts,
  getStudentProfile,
  type PublicStudentProfile,
  type StudentProfile
} from '../../services/studentApi';
import {
  getPublicTutorCertificates,
  getPublicTutorProfile,
  getTutorProfile,
  getTutorCertificates,
  updateTutorProfile,
  uploadCertificateFile,
  type PublicTutorProfile,
  type TutorCertificate,
  type TutorProfile
} from '../../services/tutorApi';
import { StudentProfilePage } from '../student/StudentProfilePage';
import { TutorCertificatesPage } from '../tutor/TutorCertificatesPage';
import { TutorProfilePage } from '../tutor/TutorProfilePage';
import { SharedProfileContracts } from './SharedProfileContracts';
import '../admin/admin.css';
import './profile-workspace.css';

type SubjectRole = 'student' | 'tutor';
type Profile = StudentProfile | PublicStudentProfile | TutorProfile | PublicTutorProfile;
type Tab = 'overview' | 'edit' | 'certificates' | 'contracts';

interface Props {
  subjectRole: SubjectRole;
  own?: boolean;
}

function populated(items: Array<[string, ReactNode | null | undefined]>): [string, ReactNode][] {
  return items.filter(
    ([, value]) => value !== null && value !== undefined && (typeof value !== 'string' || value.trim() !== '')
  ) as [string, ReactNode][];
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="admin-card admin-card-pad">
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function CertificateList({ certificates }: { certificates: TutorCertificate[] | null }) {
  if (!certificates) return <p className="admin-prose">Đang tải chứng chỉ…</p>;
  if (!certificates.length) return <p className="admin-prose">Chưa có chứng chỉ</p>;
  return (
    <ul className="admin-certificate-list">
      {certificates.map((certificate) => {
        const description = [
          certificate.issuingOrganization,
          certificate.issueDate && `Cấp ${certificate.issueDate}`,
          certificate.expiryDate && `Hết hạn ${certificate.expiryDate}`
        ]
          .filter(Boolean)
          .join(' · ');
        return (
          <li key={certificate.id}>
            <div className="admin-stack">
              <strong>{certificate.name}</strong>
              {description && <span>{description}</span>}
              {certificate.status === 'REJECTED' && certificate.rejectionReason && (
                <span className="admin-text-danger">Lý do: {certificate.rejectionReason}</span>
              )}
            </div>
            <StatusBadge meta={certificateStatusMeta[certificate.status]} />
            {certificate.certificateUrl?.startsWith('http') && (
              <a
                className="admin-button small secondary profile-document-link"
                href={certificate.certificateUrl}
                target="_blank"
                rel="noreferrer"
              >
                Xem tài liệu
              </a>
            )}
          </li>
        );
      })}
    </ul>
  );
}

export function ProfileWorkspacePage({ subjectRole, own = false }: Props) {
  const { updateProfile } = useAuth();
  const { profileId = '' } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const loadSharedCount = useCallback(
    async (signal: AbortSignal) => ({
      id: profileId,
      total: own || !profileId ? 0 : (await getSharedContracts(profileId, undefined, 0, 1, signal)).totalElements
    }),
    [own, profileId]
  );
  const sharedCount = useResource(loadSharedCount);
  const hasSharedContracts = !own && sharedCount.data?.id === profileId && sharedCount.data.total > 0;
  const tab: Tab =
    requestedTab === 'edit' && own
      ? 'edit'
      : requestedTab === 'contracts' && hasSharedContracts
        ? 'contracts'
        : requestedTab === 'certificates' && subjectRole === 'tutor'
          ? 'certificates'
          : 'overview';
  const [certificateState, setCertificateState] = useState<{ tutorId: string; items: TutorCertificate[] } | null>(null);
  const [error, setError] = useState('');
  const [photoBusy, setPhotoBusy] = useState(false);
  const [profileRevision, setProfileRevision] = useState(0);
  const loadProfile = useCallback(
    async (signal: AbortSignal): Promise<{ requestedId: string; profile: Profile }> => ({
      requestedId: profileId,
      profile: await (subjectRole === 'student'
        ? own
          ? getStudentProfile(signal)
          : getPublicStudentProfile(profileId, signal)
        : own
          ? getTutorProfile(signal)
          : getPublicTutorProfile(profileId, signal))
    }),
    [subjectRole, own, profileId]
  );
  const profileQuery = useResource(loadProfile, `${tab}-${profileRevision}`);
  const profile = profileQuery.data?.requestedId === profileId ? profileQuery.data.profile : null;
  const tutorId = subjectRole === 'tutor' && profile ? (profile as TutorProfile | PublicTutorProfile).tutorId : null;
  const certificates = certificateState?.tutorId === tutorId ? certificateState.items : null;

  useEffect(() => {
    if (!tutorId || (tab !== 'certificates' && tab !== 'overview')) return;
    const controller = new AbortController();
    (own ? getTutorCertificates(controller.signal) : getPublicTutorCertificates(tutorId, controller.signal))
      .then((items) => {
        if (!controller.signal.aborted) setCertificateState({ tutorId, items });
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setCertificateState({ tutorId, items: [] });
          setError(caught instanceof Error ? caught.message : 'Không thể tải chứng chỉ.');
        }
      });
    return () => controller.abort();
  }, [tutorId, tab, own]);

  const setTab = (next: Tab) => {
    setError('');
    if (next === 'overview' || next === 'certificates') setCertificateState(null);
    setSearchParams(next === 'overview' ? {} : { tab: next });
  };
  if (requestedTab === 'contracts' && sharedCount.isLoading)
    return <div className="profile-state">Đang tải hợp đồng…</div>;
  if (profileQuery.isLoading || (profileQuery.data && profileQuery.data.requestedId !== profileId))
    return <div className="profile-state">Đang tải hồ sơ…</div>;
  if (!profile)
    return (
      <div className="profile-state profile-error" role="alert">
        {profileQuery.error || 'Không tìm thấy hồ sơ.'}
      </div>
    );

  const name =
    [profile.firstName, profile.lastName].filter(Boolean).join(' ') ||
    (subjectRole === 'tutor' ? 'Gia sư' : 'Học viên');
  const student = subjectRole === 'student' ? (profile as StudentProfile | PublicStudentProfile) : null;
  const tutor = subjectRole === 'tutor' ? (profile as TutorProfile | PublicTutorProfile) : null;
  const location =
    student &&
    ('address' in student
      ? [student.address?.districtName, student.address?.provinceName].filter(Boolean).join(', ')
      : [student.districtName, student.provinceName].filter(Boolean).join(', '));
  const ownTutor = own && tutor ? (tutor as TutorProfile) : null;
  const showReviewSummary = ownTutor && ownTutor.profileStatus !== 'PENDING';
  const privateProfile = own ? (profile as StudentProfile | TutorProfile) : null;
  const privateAddress = privateProfile?.address;
  const genderLabels = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };
  const personalFields = populated([
    ['Họ', profile.firstName],
    ['Tên', profile.lastName],
    ...(privateProfile
      ? ([
          ['Giới tính', privateProfile.gender ? genderLabels[privateProfile.gender] : null],
          [
            'Ngày sinh',
            privateProfile.dateOfBirth ? new Date(privateProfile.dateOfBirth).toLocaleDateString('vi-VN') : null
          ],
          ['Email', privateProfile.email],
          ['Số điện thoại', privateProfile.phone],
          [
            'Địa chỉ',
            [privateAddress?.detailAddress, privateAddress?.districtName, privateAddress?.provinceName]
              .filter(Boolean)
              .join(', ')
          ]
        ] as [string, ReactNode][])
      : []),
    ...(student && !privateProfile ? [['Khu vực', location] as [string, ReactNode]] : [])
  ]);
  const studentFields = student
    ? populated([
        ['Trình độ hiện tại', student.currentLevel],
        ['Phong cách học', student.learningStyle],
        ['Mục tiêu', student.goalsDescription],
        ['Nội dung cần hỗ trợ', student.weakPoints]
      ])
    : [];
  const studentTags = student ? splitTags(student.personalityTags) : [];
  const teachingFields = tutor
    ? populated([
        ['Học vấn', tutor.education],
        ['Số năm kinh nghiệm', tutor.experienceYears === null ? null : `${tutor.experienceYears} năm`],
        ['Môn thế mạnh', tutor.strengthSubjects],
        ['Đối tượng học viên', tutor.targetStudentType]
      ])
    : [];
  const teachingTags = tutor ? splitTags(tutor.teachingStyleTags) : [];
  const activityFields = tutor
    ? populated([
        ['Đánh giá trung bình', tutor.averageRating],
        ['Số đánh giá', tutor.totalReviews],
        ['Hợp đồng hoàn thành', tutor.completedContractsCount],
        ['Học viên đã dạy', tutor.totalStudentsTaught]
      ])
    : [];
  const uploadAvatar = async (file?: File) => {
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setError('Vui lòng chọn ảnh JPG hoặc PNG.');
      return;
    }
    setPhotoBusy(true);
    setError('');
    try {
      const uploaded = await uploadCertificateFile(file);
      const updated = await updateTutorProfile({ avatarUrl: uploaded.certificateUrl });
      updateProfile(updated);
      setProfileRevision((current) => current + 1);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể cập nhật ảnh đại diện.');
    } finally {
      setPhotoBusy(false);
    }
  };

  return (
    <main className="profile-workspace">
      {!own && (
        <Link className="profile-back" to={subjectRole === 'tutor' ? '/student/tutor-offers' : '/tutor/students'}>
          ← Quay lại danh sách
        </Link>
      )}
      <header className="profile-hero">
        <div className="profile-avatar">
          {profile.avatarUrl ? <img src={profile.avatarUrl} alt="Ảnh đại diện" /> : name.charAt(0).toUpperCase()}
        </div>
        <div className="profile-hero-info">
          <span className="profile-eyebrow">{subjectRole === 'tutor' ? 'HỒ SƠ GIA SƯ' : 'HỒ SƠ HỌC VIÊN'}</span>
          <h1>{name}</h1>
          <p>
            {subjectRole === 'tutor' ? 'Gia sư' : 'Học viên'}
            {own ? ' · Hồ sơ của tôi' : ''}
            {location ? ` · ${location}` : ''}
          </p>
        </div>
        {ownTutor && (
          <div className="profile-photo-control">
            <input
              id="profile-avatar-upload"
              type="file"
              accept="image/jpeg,image/png"
              disabled={photoBusy}
              onChange={(event) => {
                void uploadAvatar(event.target.files?.[0]);
                event.target.value = '';
              }}
            />
            <label htmlFor="profile-avatar-upload" className="admin-button small outline">
              {photoBusy ? 'Đang tải ảnh…' : 'Đổi ảnh đại diện'}
            </label>
          </div>
        )}
        {ownTutor && ownTutor.profileStatus !== 'PENDING' && (
          <StatusBadge meta={profileStatusMeta[ownTutor.profileStatus]} />
        )}
      </header>
      <nav className="profile-tabs" aria-label="Mục hồ sơ">
        <button type="button" className={tab === 'overview' ? 'active' : ''} onClick={() => setTab('overview')}>
          Tổng quan
        </button>
        {own && (
          <button type="button" className={tab === 'edit' ? 'active' : ''} onClick={() => setTab('edit')}>
            Chỉnh sửa hồ sơ
          </button>
        )}
        {subjectRole === 'tutor' && (
          <button
            type="button"
            className={tab === 'certificates' ? 'active' : ''}
            onClick={() => setTab('certificates')}
          >
            Chứng chỉ
          </button>
        )}
        {hasSharedContracts && (
          <button type="button" className={tab === 'contracts' ? 'active' : ''} onClick={() => setTab('contracts')}>
            Hợp đồng ({sharedCount.data?.total})
          </button>
        )}
      </nav>
      {error && (
        <p className="profile-error" role="alert">
          {error}
        </p>
      )}
      {!own && sharedCount.error && (
        <p className="profile-error" role="alert">
          Không thể kiểm tra hợp đồng chung: {sharedCount.error}
        </p>
      )}
      {tab === 'edit' &&
        own &&
        (subjectRole === 'student' ? (
          <StudentProfilePage onSaved={() => setProfileRevision((value) => value + 1)} />
        ) : (
          <TutorProfilePage onSaved={() => setProfileRevision((value) => value + 1)} />
        ))}
      {tab === 'certificates' &&
        subjectRole === 'tutor' &&
        (own ? (
          <TutorCertificatesPage />
        ) : (
          <Section title="Chứng chỉ đã xác minh">
            <CertificateList certificates={certificates} />
          </Section>
        ))}
      {tab === 'contracts' && hasSharedContracts && (
        <SharedProfileContracts
          key={profileId}
          counterpartyId={profileId}
          viewerRole={subjectRole === 'tutor' ? 'student' : 'tutor'}
        />
      )}
      {tab === 'overview' && (
        <div className="profile-overview admin-columns">
          <div className="admin-column-main">
            {personalFields.length > 0 && (
              <Section title="Thông tin cá nhân">
                <DefinitionGrid columns={3} items={personalFields} />
              </Section>
            )}
            {profile.introduction?.trim() && (
              <Section title="Giới thiệu">
                <p className="admin-prose">{profile.introduction}</p>
              </Section>
            )}
            {student && (studentFields.length > 0 || studentTags.length > 0) && (
              <Section title="Hồ sơ học tập">
                {studentTags.length > 0 && (
                  <div className="admin-tags">
                    {studentTags.map((tag) => (
                      <span key={tag}>{tag}</span>
                    ))}
                  </div>
                )}
                {studentFields.length > 0 && <DefinitionGrid items={studentFields} />}
              </Section>
            )}
            {tutor && (
              <>
                {teachingFields.length > 0 && (
                  <Section title="Hồ sơ giảng dạy">
                    <DefinitionGrid items={teachingFields} />
                  </Section>
                )}
                {(teachingTags.length > 0 || tutor.teachingMethodology?.trim()) && (
                  <Section title="Phong cách & phương pháp giảng dạy">
                    {teachingTags.length > 0 && (
                      <div className="admin-tags">
                        {teachingTags.map((tag) => (
                          <span key={tag}>{tag}</span>
                        ))}
                      </div>
                    )}
                    {tutor.teachingMethodology?.trim() && <p className="admin-prose">{tutor.teachingMethodology}</p>}
                  </Section>
                )}
                <Section title="Chứng chỉ">
                  <CertificateList certificates={certificates} />
                </Section>
              </>
            )}
          </div>
          {(showReviewSummary || activityFields.length > 0) && (
            <aside className="admin-column-side">
              {showReviewSummary && (
                <Section title="Trạng thái duyệt">
                  <div className="admin-key-values">
                    <div>
                      <span>Hồ sơ</span>
                      <StatusBadge meta={profileStatusMeta[ownTutor.profileStatus]} />
                    </div>
                    {certificates && (
                      <div>
                        <span>Chứng chỉ đã xác minh</span>
                        <strong>
                          {certificates.filter((certificate) => certificate.status === 'VERIFIED').length}/
                          {certificates.length}
                        </strong>
                      </div>
                    )}
                  </div>
                </Section>
              )}
              {activityFields.length > 0 && (
                <Section title="Hoạt động">
                  <div className="admin-key-values">
                    {activityFields.map(([label, value]) => (
                      <div key={label}>
                        <span>{label}</span>
                        <strong>{value}</strong>
                      </div>
                    ))}
                  </div>
                </Section>
              )}
            </aside>
          )}
        </div>
      )}
    </main>
  );
}
