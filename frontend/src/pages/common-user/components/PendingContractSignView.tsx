import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ProfileNameLink } from '../../../components/common-user/ProfileNameLink';
import { Icon } from '../../../components/ui/Icon';
import { checkContractSchedule, type Contract, type ContractTerms } from '../../../services/studentApi';
import { parsePreferredSchedules } from '../../../utils/scheduleFormat';
import { ContractParticipantCard } from './ContractParticipantCard';
import { ContractTermsForm } from './ContractTermsForm';

interface BaseProps {
  contract: Contract;
  viewerRole: 'student' | 'tutor';
  backPath: string;
  busy: boolean;
  error?: string;
  notice?: string;
}

type Props = BaseProps &
  (
    | { mode: 'sign'; onSign: (terms: ContractTerms) => Promise<void>; onReject: () => void }
    | {
        mode: 'edit';
        onSave: (terms: Partial<ContractTerms>) => Promise<void>;
        onCancel: () => void;
        onRefresh: () => Promise<void>;
      }
  );

export function PendingContractSignView(props: Props) {
  const { contract, backPath, busy, error, notice, viewerRole } = props;
  const [now, setNow] = useState(0);
  useEffect(() => {
    const timer = window.setTimeout(() => setNow(Date.now()), 0);
    return () => window.clearTimeout(timer);
  }, []);
  const studentName = [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên';
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const studentSigned = props.mode === 'edit' ? viewerRole === 'student' : viewerRole === 'tutor';
  const tutorSigned = !studentSigned;
  const deadline = new Date(new Date(contract.createdAt).getTime() + 7 * 24 * 60 * 60 * 1000);
  const expired = now > 0 && (deadline.getTime() <= now || new Date(`${contract.endDate}T00:00:00`).getTime() <= now);
  const legacySchedules = contract.schedules?.length ? [] : parsePreferredSchedules(contract.preferredSchedule);
  const displayedSchedules = contract.schedules?.length ? contract.schedules : legacySchedules;
  const saveTerms = async (terms: ContractTerms) => {
    if (props.mode !== 'edit') return;
    const sameLegacySchedule =
      legacySchedules.length > 0 &&
      terms.startDate === contract.startDate &&
      terms.endDate === contract.endDate &&
      JSON.stringify(terms.schedules) === JSON.stringify(legacySchedules);
    if (sameLegacySchedule) {
      await props.onSave({
        price: terms.price,
        paymentPeriod: terms.paymentPeriod,
        preferredSchedule: contract.preferredSchedule,
        totalLessons: contract.totalLessons
      });
      return;
    }
    await props.onSave(terms);
  };

  return (
    <div className={`contract-signing-view ${props.mode === 'edit' ? 'contract-owner-edit-view' : ''}`}>
      <Link className="student-button secondary contract-create-back" to={backPath}>
        ← Quay lại danh sách hợp đồng
      </Link>
      <div className="student-page-heading contract-signing-heading">
        <h1>{props.mode === 'sign' ? 'Ký hợp đồng' : 'Chỉnh sửa hợp đồng'}</h1>
        <p>Hạn ký: {new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(deadline)}</p>
      </div>
      {notice && (
        <div className="student-notice" role="status">
          {notice}
        </div>
      )}
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <div className="student-contract-create-layout">
        <div className="student-contract-create-student">
          <ContractParticipantCard
            title="Thông tin học viên"
            icon="book"
            details={[
              {
                label: 'Họ và tên',
                value:
                  viewerRole === 'tutor' ? (
                    <ProfileNameLink role="student" profileId={contract.studentId}>
                      {studentName}
                    </ProfileNameLink>
                  ) : (
                    studentName
                  )
              },
              { label: 'Email', value: contract.studentEmail },
              { label: 'Vai trò', value: 'Học viên' },
              { label: 'Trạng thái ký', value: studentSigned ? 'Đã ký' : 'Chờ ký' }
            ]}
          />
        </div>
        <div className="student-contract-create-tutor">
          <ContractParticipantCard
            title="Thông tin gia sư"
            icon="support"
            details={[
              {
                label: 'Họ và tên',
                value:
                  viewerRole === 'student' ? (
                    <ProfileNameLink role="tutor" profileId={contract.tutorId}>
                      {tutorName}
                    </ProfileNameLink>
                  ) : (
                    tutorName
                  )
              },
              { label: 'Email', value: contract.tutorEmail },
              { label: 'Vai trò', value: 'Gia sư' },
              { label: 'Trạng thái ký', value: tutorSigned ? 'Đã ký' : 'Chờ ký' }
            ]}
          />
        </div>
        <section className="student-panel student-contract-create-terms">
          <h2 className="student-contract-panel-heading">
            <Icon name="contract" size={16} /> Điều khoản hợp đồng
          </h2>
          {props.mode === 'sign' && (
            <p className="student-contract-create-help">
              Kiểm tra các điều khoản đã được bên còn lại tạo trước khi ký. Nội dung hợp đồng không thể chỉnh sửa ở bước
              này.
            </p>
          )}
          <div className="student-contract-fixed-terms">
            <div className="student-contract-fixed-term">
              <span>Môn học</span>
              <strong>{contract.subjectName}</strong>
            </div>
            <div className="student-contract-fixed-term">
              <span>Lớp</span>
              <strong>{contract.gradeName}</strong>
            </div>
            <div className="student-contract-fixed-term">
              <span>Hình thức dạy</span>
              <strong>{contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp'}</strong>
            </div>
          </div>
          {expired ? (
            <div className="student-error contract-expired-refresh" role="alert">
              <strong>Hợp đồng đã hết hạn ký.</strong>
              <p>Thời hạn ký đã kết thúc trước khi cả hai bên hoàn tất ký hợp đồng.</p>
              {props.mode === 'edit' && (
                <button
                  type="button"
                  className="student-button primary"
                  disabled={busy}
                  onClick={() => void props.onRefresh()}
                >
                  Làm mới hợp đồng
                </button>
              )}
            </div>
          ) : (
            <ContractTermsForm
              key={contract.updatedAt}
              initial={{
                price: contract.price,
                paymentPeriod: contract.paymentPeriod,
                startDate: contract.startDate,
                endDate: contract.endDate,
                totalLessons: contract.totalLessons,
                schedules: displayedSchedules
              }}
              pdfDetails={{
                studentName,
                studentEmail: contract.studentEmail,
                studentAddress: contract.teachingMode === 'ONLINE' ? null : contract.studentDetailAddress,
                tutorName,
                tutorEmail: contract.tutorEmail,
                subject: contract.subjectName,
                grade: contract.gradeName,
                teachingMode: contract.teachingMode === 'ONLINE' ? 'Trực tuyến' : 'Trực tiếp',
                studentSigned,
                tutorSigned,
                studentSignedAt: studentSigned ? contract.createdAt : null,
                tutorSignedAt: tutorSigned ? contract.createdAt : null
              }}
              submitLabel={props.mode === 'sign' ? 'Ký hợp đồng' : 'Lưu thay đổi'}
              cancelLabel={props.mode === 'sign' ? 'Từ chối hợp đồng' : 'Hủy hợp đồng'}
              loading={busy}
              signingExisting={props.mode === 'sign'}
              includeAgreement={props.mode === 'sign'}
              showPdf={props.mode === 'edit'}
              editableScheduleList={props.mode === 'edit'}
              editingExisting={props.mode === 'edit'}
              requirePositivePrice={viewerRole === 'student'}
              validateScheduleOverlap={viewerRole === 'student'}
              onCancel={props.mode === 'sign' ? props.onReject : props.onCancel}
              onCheckSchedule={(terms) => checkContractSchedule(contract.id, terms).then((result) => result.messages)}
              onSubmit={props.mode === 'sign' ? props.onSign : saveTerms}
            />
          )}
        </section>
      </div>
    </div>
  );
}
