import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { getMyStudyingRequest, updateStudyingRequest, type StudyingRequestInput } from '../../services/studentApi';
import { useResource } from '../../hooks/useResource';
import { StudentStudyingRequestForm } from './StudentStudyingRequestForm';

export function StudentStudyingRequestEditPage() {
  const { requestId = '' } = useParams();
  const navigate = useNavigate();
  const [revision, setRevision] = useState(0);
  const load = useCallback(() => getMyStudyingRequest(requestId), [requestId]);
  const query = useResource(load, revision);
  const request = query.data;
  if (query.error)
    return (
      <div className="student-error" role="alert">
        {query.error}
      </div>
    );
  if (!request)
    return (
      <div className="student-empty">
        <h3>Đang tải yêu cầu...</h3>
      </div>
    );
  if (!['DRAFT', 'OPEN'].includes(request.status))
    return (
      <div className="student-error" role="alert">
        Yêu cầu này không còn có thể chỉnh sửa.
      </div>
    );
  const submit = async (values: StudyingRequestInput) => {
    await updateStudyingRequest(request.id, values);
    setRevision((value) => value + 1);
    navigate(`/student/studying-requests/${request.id}`, { state: { notice: 'Đã cập nhật yêu cầu tìm gia sư.' } });
  };
  return (
    <>
      <div className="student-studying-request-top-actions">
        <button
          className="student-button secondary"
          type="button"
          onClick={() => navigate(`/student/studying-requests/${request.id}`)}
        >
          ← Quay lại chi tiết yêu cầu
        </button>
      </div>
      <div className="student-page-heading">
        <div>
          <h1>Chỉnh sửa yêu cầu tìm gia sư</h1>
        </div>
      </div>
      <StudentStudyingRequestForm
        mode="edit"
        initial={request}
        onSubmit={async (values) => submit(values)}
        onCancel={() => navigate('/student/studying-requests')}
      />
    </>
  );
}
