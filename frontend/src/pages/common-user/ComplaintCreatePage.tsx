import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ProfileNameLink } from '../../components/common-user/ProfileNameLink';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import { getContracts, type Contract } from '../../services/studentApi';
import { createComplaint, type ComplaintCategory } from '../../services/studentComplaintApi';
import { ComplaintForm, type StudentComplaintFormValues } from '../../components/common-user/complaint/ComplaintForm';
import { complaintErrorMessage } from '../../components/common-user/complaint/complaintFormat';
import { Icon } from '../../components/ui/Icon';

const emptyValues: StudentComplaintFormValues = {
  contractId: '',
  category: '' as ComplaintCategory | '',
  title: '',
  content: '',
  evidences: []
};

export function ComplaintCreatePage({ basePath = '/student/complaints' }: { basePath?: string }) {
  const navigate = useNavigate();
  const role = basePath.startsWith('/tutor') ? 'tutor' : 'student';
  const [selectedContractId, setSelectedContractId] = useState(
    () => new URLSearchParams(window.location.search).get('contractId') ?? ''
  );
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const load = useCallback((signal: AbortSignal) => getContracts(undefined, 0, 100, signal), []);
  const query = useResource(load);
  const contracts: Contract[] = (query.data?.content ?? []).filter(isComplaintContractEligible);
  const eligibleSelectedId = contracts.some((contract) => contract.id === selectedContractId) ? selectedContractId : '';
  const selectedContract = contracts.find((contract) => contract.id === eligibleSelectedId);
  const respondentName = selectedContract
    ? role === 'student'
      ? [selectedContract.tutorFirstName, selectedContract.tutorLastName].filter(Boolean).join(' ') ||
        selectedContract.tutorEmail
      : [selectedContract.studentFirstName, selectedContract.studentLastName].filter(Boolean).join(' ') ||
        selectedContract.studentEmail
    : '';
  const respondentEmail = selectedContract
    ? role === 'student'
      ? selectedContract.tutorEmail
      : selectedContract.studentEmail
    : '';

  const submit = async (values: StudentComplaintFormValues) => {
    setSubmitting(true);
    setSubmitError('');
    try {
      const created = await createComplaint({
        contractId: values.contractId,
        category: values.category as ComplaintCategory,
        title: values.title.trim(),
        content: values.content.trim(),
        evidences: values.evidences
      });
      navigate(`${basePath}/${created.id}`, { state: { notice: 'Đã gửi khiếu nại.' } });
    } catch (error) {
      setSubmitError(
        error instanceof ApiError || error instanceof Error ? complaintErrorMessage(error) : 'Không thể gửi khiếu nại.'
      );
      setSubmitting(false);
    }
  };

  return (
    <div className="student-complaint-form-page">
      <Link className="student-complaint-back" to={basePath}>
        <span aria-hidden="true">←</span> Danh sách khiếu nại
      </Link>
      <div className="student-page-heading student-complaint-create-header">
        <div className="student-complaint-create-title">
          <div className="student-complaint-create-title-icon">
            <Icon name="complaint" size={25} />
          </div>
          <div>
            <h1>Tạo khiếu nại mới</h1>
          </div>
        </div>
      </div>
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      )}
      {query.isLoading && !query.data ? (
        <div className="student-complaint-state">Đang tải danh sách hợp đồng.</div>
      ) : (
        <div className="student-complaint-create-layout">
          <section className="student-panel student-complaint-create-card">
            <ComplaintForm
              mode="create"
              role={role}
              contracts={contracts}
              initialValues={{ ...emptyValues, contractId: eligibleSelectedId }}
              onContractChange={setSelectedContractId}
              submitting={submitting}
              error={submitError}
              onSubmit={(values) => void submit(values as StudentComplaintFormValues)}
              onCancel={() => navigate(basePath)}
            />
          </section>
          <div className="student-complaint-create-side">
            {selectedContract && (
              <section className="student-panel student-complaint-respondent">
                <h2>
                  <Icon name="user" size={17} /> Người bị khiếu nại
                </h2>
                <div>
                  <span>Họ và tên</span>
                  <strong>
                    <ProfileNameLink
                      role={role === 'student' ? 'tutor' : 'student'}
                      profileId={role === 'student' ? selectedContract.tutorId : selectedContract.studentId}
                    >
                      {respondentName}
                    </ProfileNameLink>
                  </strong>
                </div>
                <div>
                  <span>Vai trò</span>
                  <strong>{role === 'student' ? 'Gia sư' : 'Học viên'}</strong>
                </div>
                <div>
                  <span>Email</span>
                  <strong>{respondentEmail}</strong>
                </div>
              </section>
            )}
            <aside className="student-panel student-complaint-guidance">
              <div className="student-complaint-guidance-illustration">
                <Icon name="complaint" size={52} />
              </div>
              <h2>Lưu ý khi gửi khiếu nại</h2>
              <ul>
                {role === 'student' && (
                  <li>Gia sư vắng mặt hoặc dạy muộn, thiếu giờ: chọn buổi học và tạo yêu cầu hoàn tiền.</li>
                )}
                <li>
                  Chỉ có thể khiếu nại hợp đồng đang còn hiệu lực hoặc trong vòng 7 ngày sau ngày kết thúc hợp đồng.
                </li>
                <li>Chọn đúng hợp đồng liên quan đến vấn đề bạn gặp phải.</li>
                <li>Cung cấp tiêu đề và mô tả cụ thể, rõ ràng, khách quan.</li>
                <li>Đính kèm bằng chứng để hỗ trợ quá trình xử lý.</li>
                <li>Khiếu nại của bạn sẽ được quản trị viên tiếp nhận và xử lý theo quy định.</li>
              </ul>
            </aside>
          </div>
        </div>
      )}
    </div>
  );
}

function isComplaintContractEligible(contract: Contract) {
  if (contract.status !== 'ACTIVE' && contract.status !== 'COMPLETED') return false;
  if (!contract.endDate) return false;
  const todayParts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(new Date());
  const part = (type: string) => todayParts.find((item) => item.type === type)?.value ?? '';
  const today = `${part('year')}-${part('month')}-${part('day')}`;
  const todayDate = new Date(`${today}T00:00:00+07:00`);
  const endDate = new Date(`${contract.endDate}T00:00:00+07:00`);
  if (Number.isNaN(todayDate.getTime()) || Number.isNaN(endDate.getTime())) return false;
  return (
    todayDate.getTime() <= endDate.getTime() + 7 * 24 * 60 * 60 * 1000 &&
    (contract.status === 'ACTIVE' || todayDate.getTime() >= endDate.getTime())
  );
}
