import { useState, type FormEvent } from 'react';
import type { ComplaintEvidenceInput } from '../../../services/studentComplaintApi';
import { ComplaintEvidenceField } from './ComplaintEvidenceField';

export function ComplaintResponseForm({
  busy,
  error,
  onSubmit
}: {
  busy: boolean;
  error?: string;
  onSubmit: (response: string, evidences: ComplaintEvidenceInput[]) => void;
}) {
  const [response, setResponse] = useState('');
  const [evidences, setEvidences] = useState<ComplaintEvidenceInput[]>([]);
  const [tried, setTried] = useState(false);

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTried(true);
    if (!response.trim()) return;
    onSubmit(response.trim(), evidences);
  };

  return (
    <form className="student-panel student-complaint-response-form" onSubmit={(event) => void submit(event)} noValidate>
      <h2>Phản hồi khiếu nại</h2>
      <p>Phản hồi của bạn sẽ được gửi tới nhân viên xử lý khiếu nại.</p>
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      <label htmlFor="student-complaint-response">
        Nội dung phản hồi <span>*</span>
      </label>
      <textarea
        id="student-complaint-response"
        rows={6}
        maxLength={5000}
        value={response}
        aria-invalid={tried && !response.trim()}
        onChange={(event) => setResponse(event.target.value)}
      />
      {tried && !response.trim() && <small className="student-field-error">Vui lòng nhập nội dung phản hồi.</small>}
      <ComplaintEvidenceField value={evidences} onChange={setEvidences} />
      <div className="student-form-actions">
        <button className="student-button" type="submit" disabled={busy}>
          {busy ? 'Đang gửi…' : 'Gửi phản hồi'}
        </button>
      </div>
    </form>
  );
}
