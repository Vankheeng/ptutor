// Câu hỏi trợ lý chưa trả lời được hoặc bị 👎 (docs/support-chatbot/06 mục 6.5, 08 mục 8.2). Nội dung đã che thông
// tin cá nhân; không có mã người dùng. Mỗi dòng: "Tạo tài liệu" (điền sẵn câu hỏi) hoặc, với câu bị chặn nhầm là ngoài
// phạm vi, "Thêm làm câu mẫu trong phạm vi" (04 mục 4.11).
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { useResource } from '../../../hooks/useResource';
import { addScopeExemplar, getUnanswered } from '../../../services/adminAssistantApi';
import { AdminState } from '../components/AdminUi';
import { formatDateTime } from '../adminFormat';
import { fallbackLabels } from './kbFormat';
import './kb-admin.css';

export function UnansweredQuestionsPage() {
  const [days, setDays] = useState(30);
  const [added, setAdded] = useState<string[]>([]);
  const load = useCallback((signal: AbortSignal) => getUnanswered(days, signal), [days]);
  const { data, error, isLoading } = useResource(load);

  const markInScope = async (question: string) => {
    await addScopeExemplar(question, 'IN');
    setAdded((current) => [...current, question]);
  };

  return (
    <div className="admin-page">
      <div className="admin-section-heading">
        <h1 className="admin-title">Câu hỏi chưa trả lời được</h1>
        <label className="kb-inline">
          Trong
          <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {[7, 30, 90].map((value) => (
              <option key={value} value={value}>
                {value} ngày
              </option>
            ))}
          </select>
        </label>
      </div>
      <section className="admin-card">
        {!data ? (
          <div className="admin-card-pad">
            <AdminState loading={isLoading} error={error} />
          </div>
        ) : data.items.length === 0 ? (
          <AdminState empty="Chưa có câu hỏi nào." />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Câu hỏi</th>
                  <th>Lý do</th>
                  <th>Thời gian</th>
                  <th>
                    <span className="admin-sr-only">Thao tác</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.messageId}>
                    <td>
                      <div className="admin-stack">
                        <strong>{item.question}</strong>
                        {item.rewrittenQuery && <span>{item.rewrittenQuery}</span>}
                      </div>
                    </td>
                    <td>
                      {item.rating === -1
                        ? `👎 ${item.feedbackReason ?? ''}`
                        : (fallbackLabels[item.fallbackReason ?? ''] ?? item.fallbackReason ?? item.grounded)}
                    </td>
                    <td className="admin-nowrap">{formatDateTime(item.createdAt)}</td>
                    <td className="admin-cell-action">
                      <div className="admin-button-row">
                        <Link
                          className="admin-button small secondary"
                          to={`/admin/assistant/documents/new?question=${encodeURIComponent(item.question)}`}
                        >
                          Tạo tài liệu
                        </Link>
                        {item.fallbackReason?.startsWith('OUT_OF_SCOPE') && !added.includes(item.question) && (
                          <button
                            className="admin-button small secondary"
                            type="button"
                            onClick={() => void markInScope(item.question)}
                          >
                            Thêm làm câu mẫu trong phạm vi
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
