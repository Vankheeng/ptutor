import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createStudyingRequest, payStudyingRequest, type StudyingRequestInput } from '../../services/studentApi';
import { StudentStudyingRequestForm } from './StudentStudyingRequestForm';

export function StudentStudyingRequestCreatePage() {
  const navigate = useNavigate();
  const [draftId, setDraftId] = useState<string | null>(null);
  const [paymentError, setPaymentError] = useState('');
  const [retryingPayment, setRetryingPayment] = useState(false);

  const startPayment = async (requestId: string) => {
    const payment = await payStudyingRequest(requestId);
    if (!payment.paymentUrl) throw new Error('Chưa nhận được đường dẫn thanh toán.');
    window.location.assign(payment.paymentUrl);
  };

  const submit = async (values: StudyingRequestInput, action: 'draft' | 'pay') => {
    const created = await createStudyingRequest(values);
    setDraftId(created.id);
    if (action === 'pay') {
      try {
        setPaymentError('');
        await startPayment(created.id);
      } catch (caught) {
        setPaymentError(caught instanceof Error ? caught.message : 'Chưa mở được trang thanh toán.');
        throw caught;
      }
      return;
    }
    navigate('/student/studying-requests', { state: { notice: 'Đã lưu bản nháp tin tìm gia sư.' } });
  };
  return (
    <>
      <Link className="student-link" to="/student/studying-requests">
        ← Quay lại danh sách tin
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">NHU CẦU HỌC CỦA BẠN</span>
          <h1>Đăng tin tìm gia sư</h1>
          <p>Cho gia sư biết bạn muốn học gì và học như thế nào.</p>
        </div>
      </div>
      {paymentError && draftId && (
        <div className="student-payment-retry" role="alert">
          <span>{paymentError} Bản nháp của bạn đã được lưu.</span>
          <button
            className="student-button payment"
            type="button"
            disabled={retryingPayment}
            onClick={() => {
              setRetryingPayment(true);
              setPaymentError('');
              void startPayment(draftId)
                .catch((caught: unknown) =>
                  setPaymentError(caught instanceof Error ? caught.message : 'Chưa mở được trang thanh toán.')
                )
                .finally(() => setRetryingPayment(false));
            }}
          >
            {retryingPayment ? 'Đang mở trang thanh toán…' : 'Thanh toán lại'}
          </button>
        </div>
      )}
      <StudentStudyingRequestForm
        mode="create"
        onSubmit={submit}
        onCancel={() => navigate('/student/studying-requests')}
      />
    </>
  );
}
