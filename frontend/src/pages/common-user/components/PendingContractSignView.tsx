import { Link } from 'react-router-dom';
import { Icon } from '../../../components/ui/Icon';
import type { Contract, ContractTerms } from '../../../services/studentApi';
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
    | { mode: 'sign'; onSign: () => Promise<void>; onReject: () => void }
    | { mode: 'edit'; onSave: (terms: Partial<ContractTerms>) => Promise<void>; onCancel: () => void }
  );

export function PendingContractSignView(props: Props) {
  const { contract, backPath, busy, error, notice, viewerRole } = props;
  const studentName = [contract.studentFirstName, contract.studentLastName].filter(Boolean).join(' ') || 'Học viên';
  const tutorName = [contract.tutorFirstName, contract.tutorLastName].filter(Boolean).join(' ') || 'Gia sư';
  const studentSigned = props.mode === 'edit' ? viewerRole === 'student' : viewerRole === 'tutor';
  const tutorSigned = !studentSigned;
  const deadline = new Date(new Date(contract.createdAt).getTime() + 7 * 24 * 60 * 60 * 1000);
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
              { label: 'Họ và tên', value: studentName },
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
              { label: 'Họ và tên', value: tutorName },
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
          <p className="student-contract-create-help">
            {props.mode === 'sign'
              ? 'Kiểm tra các điều khoản đã được bên còn lại tạo trước khi ký. Nội dung hợp đồng không thể chỉnh sửa ở bước này.'
              : 'Bạn đã ký khi tạo hợp đồng. Có thể chỉnh sửa các điều khoản trong thời gian chờ bên còn lại ký.'}
          </p>
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
            editingExisting={props.mode === 'edit'}
            onCancel={props.mode === 'sign' ? props.onReject : props.onCancel}
            onSubmit={props.mode === 'sign' ? props.onSign : saveTerms}
          />
        </section>
      </div>
    </div>
  );
}
