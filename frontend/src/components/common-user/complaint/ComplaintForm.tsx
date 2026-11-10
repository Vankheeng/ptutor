import { useState, type FormEvent } from 'react';
import type { Contract } from '../../../services/studentApi';
import type {
  ComplaintCategory,
  ComplaintEvidenceInput,
  ComplaintUpdateInput
} from '../../../services/studentComplaintApi';
import { ComplaintEvidenceField } from './ComplaintEvidenceField';
import { complaintCategoryLabels } from './complaintFormat';
import { Icon, type IconName } from '../../ui/Icon';

export interface StudentComplaintFormValues {
  contractId: string;
  category: ComplaintCategory | '';
  title: string;
  content: string;
  evidences: ComplaintEvidenceInput[];
}

interface Props {
  mode: 'create' | 'edit';
  contracts: Contract[];
  initialValues: StudentComplaintFormValues;
  submitting: boolean;
  error?: string;
  formId?: string;
  hideActions?: boolean;
  cancelLabel?: string;
  submitLabel?: string;
  onSubmit: (values: StudentComplaintFormValues | ComplaintUpdateInput) => void;
  onCancel: () => void;
}

const categories = Object.keys(complaintCategoryLabels) as ComplaintCategory[];
const categoryIcons: Record<ComplaintCategory, IconName> = {
  ABSENCE: 'calendar',
  LATE_OR_SHORT: 'bell',
  TEACHING_QUALITY: 'book',
  MISCONDUCT: 'user',
  SERIOUS_MISCONDUCT: 'shield',
  OTHER: 'menu'
};

export function ComplaintForm({
  mode,
  contracts,
  initialValues,
  submitting,
  error,
  formId,
  hideActions = false,
  cancelLabel = 'Hủy',
  submitLabel,
  onSubmit,
  onCancel
}: Props) {
  const [values, setValues] = useState(initialValues);
  const [tried, setTried] = useState(false);

  const titleError = tried && !values.title.trim();
  const contentError = tried && !values.content.trim();
  const categoryError = tried && !values.category;
  const contractError = mode === 'create' && tried && !values.contractId;
  const titleMaxLength = mode === 'create' ? 200 : 255;
  const contentMaxLength = mode === 'create' ? 2000 : 10000;

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setTried(true);
    if (titleError || contentError || categoryError || contractError) return;

    if (mode === 'edit') {
      onSubmit({
        category: values.category as ComplaintCategory,
        title: values.title.trim(),
        content: values.content.trim(),
        evidences: values.evidences
      });
      return;
    }
    onSubmit(values);
  };

  const selectedContract = contracts.find((contract) => contract.id === values.contractId);
  const contractLabel = (contract: Contract) => {
    const subject = contract.subjectName || 'Hợp đồng';
    const code = `HD-${contract.id.slice(0, 8).toUpperCase()}`;
    return `${code} · ${subject} · ${contract.gradeName || 'Chưa cập nhật lớp'}`;
  };

  return (
    <form id={formId} className="student-complaint-form" onSubmit={(event) => void submit(event)} noValidate>
      {error && (
        <div className="student-error" role="alert">
          {error}
        </div>
      )}
      {mode === 'create' ? (
        <div className="student-form-field">
          <label htmlFor="student-complaint-contract">
            Hợp đồng <span>*</span>
          </label>
          <select
            id="student-complaint-contract"
            value={values.contractId}
            aria-invalid={contractError}
            onChange={(event) => setValues((current) => ({ ...current, contractId: event.target.value }))}
          >
            <option value="">Chọn hợp đồng</option>
            {contracts.map((contract) => (
              <option key={contract.id} value={contract.id}>
                {contractLabel(contract)}
              </option>
            ))}
          </select>
          {contractError && <small className="student-field-error">Vui lòng chọn hợp đồng.</small>}
        </div>
      ) : (
        <div className="student-complaint-readonly-contract">
          <span>Hợp đồng liên quan</span>
          <strong>{selectedContract ? contractLabel(selectedContract) : `#${values.contractId.slice(0, 8)}`}</strong>
        </div>
      )}

      <div className="student-form-field student-complaint-category-field">
        <span className="student-complaint-field-label">
          Loại khiếu nại <span>*</span>
        </span>
        <div className="student-complaint-category-options" role="radiogroup" aria-label="Loại khiếu nại">
          {categories.map((category) => (
            <label
              className={`${category.toLowerCase()}${values.category === category ? ' selected' : ''}`}
              key={category}
            >
              <input
                type="radio"
                name="student-complaint-category"
                value={category}
                checked={values.category === category}
                onChange={() => setValues((current) => ({ ...current, category }))}
              />
              <span className="student-complaint-radio" aria-hidden="true" />
              <Icon name={categoryIcons[category]} size={16} />
              <span>{complaintCategoryLabels[category]}</span>
            </label>
          ))}
        </div>
        {categoryError && <small className="student-field-error">Vui lòng chọn loại khiếu nại.</small>}
      </div>

      <div className="student-form-field">
        <label htmlFor="student-complaint-title">
          Tiêu đề <span>*</span>
        </label>
        <input
          id="student-complaint-title"
          maxLength={titleMaxLength}
          placeholder="Nhập tiêu đề ngắn gọn về vấn đề bạn gặp phải..."
          value={values.title}
          aria-invalid={titleError}
          onChange={(event) => setValues((current) => ({ ...current, title: event.target.value }))}
        />
        {titleError && <small className="student-field-error">Vui lòng nhập tiêu đề.</small>}
      </div>

      <div className="student-form-field">
        <label htmlFor="student-complaint-content">
          Nội dung mô tả <span>*</span>
        </label>
        <textarea
          id="student-complaint-content"
          rows={7}
          maxLength={contentMaxLength}
          placeholder="Mô tả chi tiết sự việc, thời gian, bối cảnh và các thông tin liên quan..."
          value={values.content}
          aria-invalid={contentError}
          onChange={(event) => setValues((current) => ({ ...current, content: event.target.value }))}
        />
        <div className="student-form-counter">
          {values.content.length}/{contentMaxLength}
        </div>
        {contentError && <small className="student-field-error">Vui lòng nhập nội dung khiếu nại.</small>}
      </div>

      <ComplaintEvidenceField
        label="Minh chứng"
        addLabel="Thêm minh chứng"
        value={values.evidences}
        onChange={(evidences) => setValues((current) => ({ ...current, evidences }))}
      />

      {!hideActions && (
        <div className="student-form-actions">
          <button className="student-button secondary" type="button" disabled={submitting} onClick={onCancel}>
            {cancelLabel}
          </button>
          <button className="student-button" type="submit" disabled={submitting}>
            {submitting
              ? 'Đang gửi…'
              : (submitLabel ??
                (mode === 'create' ? (
                  <>
                    <Icon name="send" size={15} /> Gửi khiếu nại
                  </>
                ) : (
                  'Lưu thay đổi'
                )))}
          </button>
        </div>
      )}
    </form>
  );
}
