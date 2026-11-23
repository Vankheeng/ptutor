import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  createStudyingRequest,
  payStudyingRequest,
  payStudyingRequestWithWallet,
  type StudyingRequestInput
} from '../../services/studentApi';
import { StudentStudyingRequestForm } from './StudentStudyingRequestForm';

type PaymentMethod = 'wallet' | 'vnpay';

export function StudentStudyingRequestCreatePage() {
  const navigate = useNavigate();
  const [draftId, setDraftId] = useState<string | null>(null);
  const [paymentError, setPaymentError] = useState(false);
  const [submittingPayment, setSubmittingPayment] = useState(false);

  const startPayment = async (requestId: string, method: PaymentMethod) => {
    if (method === 'wallet') {
      await payStudyingRequestWithWallet(requestId);
      navigate('/student/studying-requests', {
        state: { notice: 'Đã thanh toán phí bằng Ví PTutor. Yêu cầu đang chờ hệ thống xử lý.' }
      });
      return;
    }

    const payment = await payStudyingRequest(requestId);
    if (!payment.paymentUrl) throw new Error('VNPay không trả về liên kết thanh toán.');
    let paymentUrl: URL;
    try {
      paymentUrl = new URL(payment.paymentUrl);
    } catch {
      throw new Error('Liên kết thanh toán VNPay không hợp lệ.');
    }
    if (!['http:', 'https:'].includes(paymentUrl.protocol)) {
      throw new Error('Liên kết thanh toán VNPay không hợp lệ.');
    }
    window.location.assign(paymentUrl.toString());
  };

  const submit = async (values: StudyingRequestInput, action: 'draft' | 'pay', paymentMethod: PaymentMethod) => {
    if (draftId) {
      setPaymentError(false);
      if (action === 'draft') {
        navigate('/student/studying-requests', {
          state: { notice: 'Bản nháp đã được lưu. Bạn có thể thanh toán sau từ danh sách yêu cầu.' }
        });
        return;
      }

      setSubmittingPayment(true);
      try {
        await startPayment(draftId, paymentMethod);
      } catch {
        setPaymentError(true);
      } finally {
        setSubmittingPayment(false);
      }
      return;
    }

    const created = await createStudyingRequest(values);
    if (action === 'draft') {
      navigate('/student/studying-requests', { state: { notice: 'Đã tạo bản nháp yêu cầu tìm gia sư.' } });
      return;
    }

    setDraftId(created.id);
    setSubmittingPayment(true);
    setPaymentError(false);
    try {
      await startPayment(created.id, paymentMethod);
    } catch {
      setPaymentError(true);
    } finally {
      setSubmittingPayment(false);
    }
  };

  return (
    <>
      <div className="student-studying-request-top-actions">
        <button
          className="student-button secondary"
          type="button"
          onClick={() => navigate('/student/studying-requests')}
          disabled={submittingPayment}
        >
          ← Quay lại danh sách
        </button>
      </div>
      <div className="student-page-heading">
        <div>
          <h1>Tạo yêu cầu tìm gia sư</h1>
        </div>
      </div>

      {paymentError && draftId && (
        <div className="student-studying-request-payment-error" role="alert">
          <span>Thanh toán không thành công. Đã tạo bản nháp</span>
          <button type="button" aria-label="Đóng thông báo thanh toán" onClick={() => setPaymentError(false)}>
            ×
          </button>
        </div>
      )}
      <StudentStudyingRequestForm
        mode="create"
        locked={draftId !== null}
        onSubmit={submit}
        onCancel={() => navigate('/student/studying-requests')}
      />
    </>
  );
}
