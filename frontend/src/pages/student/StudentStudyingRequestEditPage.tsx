import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
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
        <h3>Đang tải tin…</h3>
      </div>
    );
  if (!['DRAFT', 'OPEN'].includes(request.status))
    return (
      <div className="student-error" role="alert">
        Tin này không còn sửa được.
      </div>
    );
  const submit = async (values: StudyingRequestInput) => {
    await updateStudyingRequest(request.id, values);
    setRevision((value) => value + 1);
    navigate(`/student/studying-requests/${request.id}`, { state: { notice: 'Đã cập nhật tin.' } });
  };
  return (
    <>
      <Link className="student-link" to={`/student/studying-requests/${request.id}`}>
        ← Quay lại chi tiết tin
      </Link>
      <div className="student-page-heading">
        <div>
          <span className="student-eyebrow">TIN TÌM GIA SƯ</span>
          <h1>Sửa tin tìm gia sư</h1>
          <p>Bạn sửa được tin khi tin còn là bản nháp hoặc đang mở.</p>
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
