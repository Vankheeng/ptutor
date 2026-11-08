import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useResource } from '../../hooks/useResource';
import { ApiError } from '../../services/httpClient';
import { getContracts, type Contract } from '../../services/studentApi';
import { createComplaint, type ComplaintCategory } from '../../services/studentComplaintApi';
import {
  StudentComplaintForm,
  type StudentComplaintFormValues
} from '../../components/student/complaint/StudentComplaintForm';
import { complaintErrorMessage } from '../../components/student/complaint/studentComplaintFormat';
import { Icon } from '../../components/ui/Icon';

const emptyValues: StudentComplaintFormValues = {
  contractId: '',
  category: '' as ComplaintCategory | '',
  title: '',
  content: '',
  evidences: []
};

export function StudentComplaintCreatePage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const load = useCallback((signal: AbortSignal) => getContracts(undefined, 0, 100, signal), []);
  const query = useResource(load);
  const contracts: Contract[] = query.data?.content ?? [];

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
      navigate(`/student/complaints/${created.id}`, { state: { notice: 'Đã gửi khiếu nại.' } });
    } catch (error) {
      setSubmitError(
        error instanceof ApiError || error instanceof Error ? complaintErrorMessage(error) : 'Không thể gửi khiếu nại.'
      );
      setSubmitting(false);
    }
  };

  return (
    <div className="student-complaint-form-page">
      <Link className="student-complaint-back" to="/student/complaints">
        <span aria-hidden="true">←</span> Danh sách khiếu nại
      </Link>
      <div className="student-page-heading student-complaint-create-header">
        <div className="student-complaint-create-title">
          <div className="student-complaint-create-title-icon">
            <Icon name="complaint" size={25} />
          </div>
          <div>
            <h1>Tạo khiếu nại mới</h1>
            <p>Gửi khiếu nại về vấn đề phát sinh trong quá trình học tập hoặc hợp đồng.</p>
          </div>
        </div>
      </div>
      {query.error && (
        <div className="student-error" role="alert">
          {query.error}
        </div>
      )}
      {query.isLoading && !query.data ? (
        <div className="student-complaint-state">Đang tải danh sách hợp đồng…</div>
      ) : (
        <div className="student-complaint-create-layout">
          <section className="student-panel student-complaint-create-card">
            <StudentComplaintForm
              mode="create"
              contracts={contracts}
              initialValues={emptyValues}
              submitting={submitting}
              error={submitError}
              onSubmit={(values) => void submit(values as StudentComplaintFormValues)}
              onCancel={() => navigate('/student/complaints')}
            />
          </section>
          <aside className="student-panel student-complaint-guidance">
            <div className="student-complaint-guidance-illustration">
              <Icon name="complaint" size={52} />
            </div>
            <h2>Lưu ý khi gửi khiếu nại</h2>
            <ul>
              <li>Chọn đúng hợp đồng liên quan đến vấn đề bạn gặp phải.</li>
              <li>Cung cấp tiêu đề và mô tả cụ thể, rõ ràng, khách quan.</li>
              <li>Đính kèm đường dẫn minh chứng để hỗ trợ quá trình xử lý.</li>
              <li>Khiếu nại của bạn sẽ được quản trị viên tiếp nhận và xử lý theo quy định.</li>
            </ul>
          </aside>
        </div>
      )}
    </div>
  );
}
