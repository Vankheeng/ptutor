import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import {
  approveTutorProfile,
  getAdminTutorCertificates,
  rejectTutorProfile,
  type AdminTutorDetail
} from '../../services/adminTutorApi';
import {
  accountStatusMeta,
  certificateStatusMeta,
  formatDate,
  formatDateTime,
  formatNumber,
  genderLabels,
  isExpired,
  profileStatusMeta,
  splitTags
} from './adminFormat';
import { useAdminTutor } from './adminTutorContext';
import { AdminModal, AdminState, DefinitionGrid, ReasonField, StatusBadge } from './components/AdminUi';
import { CertificateReviewModal } from './components/CertificateReviewModal';

const MIN_REASON = 10;
const MAX_REASON = 500;
const reasonPresets = [
  'Thông tin học vấn chưa khớp với chứng chỉ đã nộp.',
  'Phần giới thiệu và phương pháp giảng dạy còn quá sơ sài, cần mô tả cụ thể hơn.',
  'Ảnh đại diện không rõ mặt, vui lòng cập nhật ảnh chân dung.'
];

type Dialog = 'approve' | 'reject' | null;

export function AdminTutorReviewPage() {
  const { tutor, reload } = useAdminTutor();
  const [certificateRevision, setCertificateRevision] = useState(0);
  const loadCertificates = useCallback(
    async (signal: AbortSignal) => (await getAdminTutorCertificates(tutor.userId, undefined, signal)).content,
    [tutor.userId]
  );
  const certificates = useResource(loadCertificates, certificateRevision);
  const [openCertificateId, setOpenCertificateId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [reason, setReason] = useState('');
  const [reasonTouched, setReasonTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState('');
  const [result, setResult] = useState<'approved' | 'rejected' | null>(null);

  const certificateList = certificates.data ?? [];
  const pendingCertificates = certificateList.filter((certificate) => certificate.status === 'PENDING').length;
  const verifiedCertificates = certificateList.filter((certificate) => certificate.status === 'VERIFIED').length;
  const isPending = tutor.profileStatus === 'PENDING';

  const decide = async (action: () => Promise<AdminTutorDetail>, outcome: 'approved' | 'rejected') => {
    setSubmitting(true);
    setActionError('');
    try {
      await action();
      setDialog(null);
      setResult(outcome);
      reload();
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'TUTOR_PROFILE_ALREADY_REVIEWED') {
        setDialog(null);
        setActionError('Hồ sơ này vừa được người khác xử lý. Đã tải lại trạng thái mới nhất.');
        reload();
      } else {
        setActionError(caught instanceof Error ? caught.message : 'Không thể xử lý hồ sơ.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const confirmReject = () => {
    setReasonTouched(true);
    if (reason.trim().length < MIN_REASON) return;
    void decide(() => rejectTutorProfile(tutor.userId, reason.trim()), 'rejected');
  };

  const openReject = () => {
    setActionError('');
    setReasonTouched(false);
    setDialog('reject');
  };

  return (
    <>
      {result && (
        <div
          className={`admin-alert ${result === 'approved' ? 'admin-alert-success' : 'admin-alert-danger'}`}
          role="status"
        >
          <strong>{result === 'approved' ? 'Đã phê duyệt hồ sơ.' : 'Đã từ chối hồ sơ.'}</strong>
          <span>Đã gửi thông báo cho gia sư.</span>
        </div>
      )}
      {actionError && !dialog && (
        <div className="admin-alert admin-alert-danger" role="alert">
          {actionError}
        </div>
      )}
      {tutor.profileStatus === 'REJECTED' && (
        <div className="admin-alert admin-alert-danger">
          <strong>
            Hồ sơ đã bị từ chối bởi {tutor.profileReviewedByName || '—'} · {formatDateTime(tutor.profileReviewedAt)}
          </strong>
          {tutor.profileRejectionReason && <span>Lý do: {tutor.profileRejectionReason}</span>}
        </div>
      )}

      <div className="admin-columns">
        <div className="admin-column-main">
          <section className="admin-card admin-card-pad">
            <h2>Thông tin cá nhân</h2>
            <DefinitionGrid
              columns={3}
              items={[
                ['Họ', tutor.firstName || '—'],
                ['Tên', tutor.lastName || '—'],
                ['Giới tính', tutor.gender ? genderLabels[tutor.gender] : '—'],
                ['Ngày sinh', formatDate(tutor.dateOfBirth)],
                [
                  'Địa chỉ',
                  [tutor.address?.detailAddress, tutor.address?.districtName, tutor.address?.provinceName]
                    .filter(Boolean)
                    .join(', ') || '—'
                ]
              ]}
            />
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Giới thiệu</h2>
            <p className="admin-prose">{tutor.introduction || 'Chưa có giới thiệu.'}</p>
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Học vấn &amp; chuyên môn</h2>
            <DefinitionGrid
              items={[
                ['Học vấn', tutor.education || '—'],
                ['Số năm kinh nghiệm', tutor.experienceYears !== null ? `${tutor.experienceYears} năm` : '—'],
                ['Môn thế mạnh', tutor.strengthSubjects || '—'],
                ['Đối tượng học viên', tutor.targetStudentType || '—']
              ]}
            />
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Phong cách &amp; phương pháp giảng dạy</h2>
            {splitTags(tutor.teachingStyleTags).length > 0 && (
              <div className="admin-tags">
                {splitTags(tutor.teachingStyleTags).map((tag) => (
                  <span key={tag}>{tag}</span>
                ))}
              </div>
            )}
            <p className="admin-prose">{tutor.teachingMethodology || 'Chưa mô tả phương pháp giảng dạy.'}</p>
          </section>
          <section className="admin-card admin-card-pad">
            <div className="admin-section-heading">
              <h2>Chứng chỉ</h2>
              {certificates.data && (
                <span>
                  {certificateList.length - pendingCertificates}/{certificateList.length} đã xử lý
                </span>
              )}
            </div>
            {!certificates.data ? (
              <AdminState loading={certificates.isLoading} error={certificates.error} />
            ) : certificateList.length === 0 ? (
              <AdminState empty="Gia sư chưa nộp chứng chỉ nào." />
            ) : (
              <ul className="admin-certificate-list">
                {certificateList.map((certificate) => {
                  const pending = certificate.status === 'PENDING';
                  return (
                    <li key={certificate.id}>
                      <div className="admin-stack">
                        <strong>{certificate.name}</strong>
                        <span>
                          {certificate.issuingOrganization || '—'} · Cấp {formatDate(certificate.issueDate)} · Hết hạn{' '}
                          {certificate.expiryDate ? formatDate(certificate.expiryDate) : 'không thời hạn'}
                        </span>
                        {certificate.status === 'REJECTED' && certificate.rejectionReason && (
                          <span className="admin-text-danger">Lý do: {certificate.rejectionReason}</span>
                        )}
                        {pending && isExpired(certificate.expiryDate) && (
                          <span className="admin-text-warning">Đã hết hạn, không thể xác minh</span>
                        )}
                      </div>
                      <StatusBadge meta={certificateStatusMeta[certificate.status]} />
                      <button
                        className={`admin-button small${pending ? ' outline' : ' secondary'}`}
                        type="button"
                        onClick={() => setOpenCertificateId(certificate.id)}
                      >
                        {pending ? 'Xem & xác minh' : 'Xem'}
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        </div>

        <aside className="admin-column-side">
          <section className="admin-card admin-card-pad">
            <h2>Trạng thái duyệt</h2>
            <div className="admin-key-values">
              <div>
                <span>Hồ sơ</span>
                <StatusBadge meta={profileStatusMeta[tutor.profileStatus]} />
              </div>
              <div>
                <span>Người duyệt</span>
                <strong>{tutor.profileReviewedByName || '—'}</strong>
              </div>
              <div>
                <span>Thời gian</span>
                <strong>{formatDateTime(tutor.profileReviewedAt)}</strong>
              </div>
              <div>
                <span>Chứng chỉ đã xác minh</span>
                <strong>
                  {verifiedCertificates}/{certificateList.length}
                </strong>
              </div>
            </div>
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Tài khoản</h2>
            <div className="admin-key-values">
              <div>
                <span>Trạng thái</span>
                <StatusBadge meta={accountStatusMeta[tutor.accountStatus]} />
              </div>
              <div>
                <span>Số lần đình chỉ</span>
                <strong>{tutor.suspensionCount}</strong>
              </div>
              {tutor.suspensionType && (
                <div>
                  <span>Đình chỉ hiện tại</span>
                  <strong>
                    {tutor.suspensionType === 'PERMANENT' ? 'Vĩnh viễn' : `Đến ${formatDateTime(tutor.suspendedUntil)}`}
                  </strong>
                </div>
              )}
            </div>
          </section>
          <section className="admin-card admin-card-pad">
            <h2>Hoạt động</h2>
            <div className="admin-key-values">
              <div>
                <span>Đánh giá</span>
                <strong>
                  {tutor.totalReviews ? `${formatNumber(tutor.averageRating, 1)} (${tutor.totalReviews})` : '—'}
                </strong>
              </div>
              <div>
                <span>Hợp đồng hoàn thành</span>
                <strong>{tutor.completedContractsCount ?? 0}</strong>
              </div>
              <div>
                <span>Học viên đã dạy</span>
                <strong>{tutor.totalStudentsTaught ?? 0}</strong>
              </div>
              <div>
                <span>Tỉ lệ nhận yêu cầu</span>
                <strong>{tutor.acceptanceRate !== null ? `${formatNumber(tutor.acceptanceRate, 0)}%` : '—'}</strong>
              </div>
              <div>
                <span>Phản hồi trung bình</span>
                <strong>
                  {tutor.avgResponseTimeHours !== null ? `${formatNumber(tutor.avgResponseTimeHours, 1)} giờ` : '—'}
                </strong>
              </div>
            </div>
          </section>
        </aside>
      </div>

      {isPending && (
        <div className="admin-action-bar">
          <span>
            {pendingCertificates > 0 ? `Còn ${pendingCertificates} chứng chỉ chưa xử lý.` : 'Đã xử lý hết chứng chỉ.'}
          </span>
          <div className="admin-button-row">
            <button className="admin-button danger-outline large" type="button" onClick={openReject}>
              Từ chối hồ sơ
            </button>
            <button
              className="admin-button large"
              type="button"
              onClick={() => {
                setActionError('');
                setDialog('approve');
              }}
            >
              Phê duyệt hồ sơ
            </button>
          </div>
        </div>
      )}

      {dialog === 'approve' && (
        <AdminModal
          size="sm"
          title="Phê duyệt hồ sơ?"
          onClose={() => setDialog(null)}
          footer={
            <>
              <button className="admin-button secondary" type="button" onClick={() => setDialog(null)}>
                Quay lại kiểm tra
              </button>
              <button
                className="admin-button"
                type="button"
                disabled={submitting}
                onClick={() => void decide(() => approveTutorProfile(tutor.userId), 'approved')}
              >
                {submitting ? 'Đang phê duyệt…' : 'Phê duyệt'}
              </button>
            </>
          }
        >
          <div className="admin-modal-pad admin-stack-gap">
            {pendingCertificates > 0 && (
              <div className="admin-alert admin-alert-warning">
                Còn <strong>{pendingCertificates}</strong> chứng chỉ chưa xử lý.
              </div>
            )}
            {actionError && (
              <div className="admin-alert admin-alert-danger" role="alert">
                {actionError}
              </div>
            )}
          </div>
        </AdminModal>
      )}

      {dialog === 'reject' && (
        <AdminModal
          title="Từ chối hồ sơ"
          onClose={() => setDialog(null)}
          footer={
            <>
              <button className="admin-button secondary" type="button" onClick={() => setDialog(null)}>
                Hủy
              </button>
              <button className="admin-button danger" type="button" disabled={submitting} onClick={confirmReject}>
                {submitting ? 'Đang gửi…' : 'Xác nhận từ chối'}
              </button>
            </>
          }
        >
          <div className="admin-modal-pad admin-stack-gap">
            <div className="admin-presets">
              <span>Lý do thường gặp</span>
              <div>
                {reasonPresets.map((preset) => (
                  <button key={preset} type="button" onClick={() => setReason(preset)}>
                    {preset}
                  </button>
                ))}
              </div>
            </div>
            <ReasonField
              id="profile-reason"
              label="Lý do từ chối"
              value={reason}
              minLength={MIN_REASON}
              maxLength={MAX_REASON}
              showError={reasonTouched}
              placeholder="Nêu rõ thông tin nào chưa đạt và gia sư cần bổ sung gì."
              onChange={setReason}
            />
            {actionError && (
              <div className="admin-alert admin-alert-danger" role="alert">
                {actionError}
              </div>
            )}
          </div>
        </AdminModal>
      )}

      {openCertificateId && (
        <CertificateReviewModal
          certificateId={openCertificateId}
          onClose={() => setOpenCertificateId(null)}
          onReviewed={() => {
            setOpenCertificateId(null);
            setCertificateRevision((value) => value + 1);
            reload();
          }}
        />
      )}
    </>
  );
}
