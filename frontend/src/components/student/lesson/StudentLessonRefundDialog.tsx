import { useState } from 'react';
import type { Contract } from '../../../services/studentApi';
import {
  createRefundRequest,
  type AbsenceOutcome,
  type ContractLesson,
  type EvidenceInput
} from '../../../services/refundApi';
import { refundErrorMessage } from '../../refund/refundFormat';
import { StudentRefundEvidenceField } from './StudentRefundEvidenceField';
import '../../refund/refund.css';

type Props = {
  lesson: ContractLesson;
  contract?: Contract;
  onClose: () => void;
  onSubmitted: () => void;
};

const claimOptions: { value: AbsenceOutcome; label: string }[] = [
  { value: 'TUTOR_ABSENT', label: 'Gia sư không dạy' },
  { value: 'STUDENT_ABSENT', label: 'Tôi vắng buổi này' }
];

const formatDate = (value: string) => {
  const [year, month, day] = value.split('-');
  return year && month && day ? `${day}/${month}/${year}` : value;
};

export function StudentLessonRefundDialog({ lesson, contract, onClose, onSubmitted }: Props) {
  const [claimedOutcome, setClaimedOutcome] = useState<AbsenceOutcome>('TUTOR_ABSENT');
  const [description, setDescription] = useState('');
  const [evidences, setEvidences] = useState<EvidenceInput[]>([]);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const submit = async () => {
    if (!description.trim()) {
      setFailure('Vui lòng nhập nội dung khiếu nại hoặc yêu cầu hoàn tiền.');
      return;
    }

    setBusy(true);
    setFailure(null);
    try {
      await createRefundRequest({
        contractId: lesson.contractId,
        description: description.trim(),
        lessons: [{ lessonId: lesson.id, claimedOutcome }],
        evidences
      });
      onSubmitted();
    } catch (error) {
      setFailure(refundErrorMessage(error));
      setBusy(false);
    }
  };

  return (
    <div className="student-lesson-modal-overlay" role="presentation" onMouseDown={onClose}>
      <section
        className="student-lesson-refund-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="student-lesson-refund-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className="student-lesson-refund-dialog-header">
          <div>
            <span className="student-lesson-dialog-kicker">KHIẾU NẠI / HOÀN TIỀN</span>
            <h2 id="student-lesson-refund-title">Gửi yêu cầu cho buổi học</h2>
          </div>
          <button type="button" className="student-lesson-dialog-close" onClick={onClose} aria-label="Đóng">
            ×
          </button>
        </header>

        <div className="student-lesson-refund-summary">
          <strong>{lesson.title || contract?.subjectName || 'Buổi học'}</strong>
          <span>
            {formatDate(lesson.date)} · {lesson.startTime.slice(0, 5)} – {lesson.endTime.slice(0, 5)}
          </span>
          <span>Hợp đồng: {contract?.subjectName || lesson.contractId.slice(0, 8)}</span>
        </div>

        <fieldset className="student-lesson-claim-options">
          <legend>Lý do yêu cầu</legend>
          {claimOptions.map((option) => (
            <label key={option.value} className={claimedOutcome === option.value ? 'selected' : ''}>
              <input
                type="radio"
                name="student-lesson-claim"
                checked={claimedOutcome === option.value}
                onChange={() => setClaimedOutcome(option.value)}
              />
              <span>{option.label}</span>
            </label>
          ))}
        </fieldset>

        <label className="student-lesson-dialog-field">
          <span>
            Nội dung <b>*</b>
          </span>
          <textarea
            rows={5}
            maxLength={2000}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Mô tả chi tiết vấn đề của buổi học..."
          />
          <small>{description.length}/2000</small>
        </label>

        <StudentRefundEvidenceField value={evidences} onChange={setEvidences} />

        {failure && (
          <div className="student-lesson-dialog-error" role="alert">
            {failure}
          </div>
        )}

        <footer className="student-lesson-refund-dialog-actions">
          <button type="button" className="student-lesson-dialog-secondary" onClick={onClose} disabled={busy}>
            Hủy
          </button>
          <button type="button" className="student-lesson-dialog-submit" onClick={() => void submit()} disabled={busy}>
            {busy ? 'Đang gửi...' : 'Gửi yêu cầu'}
          </button>
        </footer>
      </section>
    </div>
  );
}
