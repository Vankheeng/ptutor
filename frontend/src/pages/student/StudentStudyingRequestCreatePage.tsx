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
    if (!payment.paymentUrl) throw new Error('Payment URL was not returned.');
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
        setPaymentError(caught instanceof Error ? caught.message : 'Payment could not be started.');
        throw caught;
      }
      return;
    }
    navigate('/student/studying-requests', { state: { notice: 'Studying request draft created.' } });
  };
  return (
    <>
      <Link className="student-link" to="/student/studying-requests">
        ← Back to studying requests
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">YOUR LEARNING NEEDS</span>
          <h1>Create a studying request</h1>
          <p>Tell tutors what and how you want to learn.</p>
        </div>
      </div>
      {paymentError && draftId && (
        <div className="student-payment-retry" role="alert">
          <span>{paymentError} Your draft has been saved.</span>
          <button
            className="student-button payment"
            type="button"
            disabled={retryingPayment}
            onClick={() => {
              setRetryingPayment(true);
              setPaymentError('');
              void startPayment(draftId)
                .catch((caught: unknown) =>
                  setPaymentError(caught instanceof Error ? caught.message : 'Payment could not be started.')
                )
                .finally(() => setRetryingPayment(false));
            }}
          >
            {retryingPayment ? 'Opening payment…' : 'Retry payment'}
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
