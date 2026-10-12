import { Link, useNavigate } from 'react-router-dom';
import { createStudyingRequest, payStudyingRequest, type StudyingRequestInput } from '../../services/studentApi';
import { StudentStudyingRequestForm } from './StudentStudyingRequestForm';

export function StudentStudyingRequestCreatePage() {
  const navigate = useNavigate();
  const submit = async (values: StudyingRequestInput, action: 'draft' | 'pay') => {
    const created = await createStudyingRequest(values);
    if (action === 'pay') {
      try {
        const payment = await payStudyingRequest(created.id);
        if (!payment.paymentUrl) throw new Error('Payment URL was not returned.');
        window.location.assign(payment.paymentUrl);
      } catch {
        navigate('/student/studying-requests', {
          state: { notice: 'Draft created, but payment could not be started. You can retry from the request menu.' }
        });
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
      <StudentStudyingRequestForm
        mode="create"
        onSubmit={submit}
        onCancel={() => navigate('/student/studying-requests')}
      />
    </>
  );
}
