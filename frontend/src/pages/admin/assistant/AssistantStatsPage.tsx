// Số liệu tổng quan của trợ lý 30 ngày gần nhất (docs/support-chatbot/07 mục 7.3, 11 mục 11.3).
import { useCallback } from 'react';
import { useResource } from '../../../hooks/useResource';
import { getAssistantStats } from '../../../services/adminAssistantApi';
import { AdminState } from '../components/AdminUi';
import { fallbackLabels } from './kbFormat';
import './kb-admin.css';

const groundedLabels: Record<string, string> = { FULL: 'Có căn cứ', PARTIAL: 'Một phần', NONE: 'Từ chối' };

function percent(part: number, total: number): string {
  return total ? `${Math.round((100 * part) / total)}%` : '—';
}

export function AssistantStatsPage() {
  const load = useCallback((signal: AbortSignal) => getAssistantStats(signal), []);
  const { data, error, isLoading } = useResource(load);
  if (!data) return <AdminState loading={isLoading} error={error} />;
  const votes = data.feedback.up + data.feedback.down;

  return (
    <div className="admin-page">
      <h1 className="admin-title">Thống kê trợ lý (30 ngày)</h1>
      <div className="admin-stats">
        <div className="admin-stat admin-stat-brand">
          <span>Số lượt</span>
          <strong className="admin-big-number">{data.turns.toLocaleString('vi-VN')}</strong>
        </div>
        <div className="admin-stat admin-stat-success">
          <span>Có căn cứ</span>
          <strong className="admin-big-number">{percent(data.grounded.FULL ?? 0, data.turns)}</strong>
        </div>
        <div className="admin-stat admin-stat-info">
          <span>Độ trễ p50 / p95</span>
          <strong className="admin-big-number">
            {(data.latencyMs.p50 / 1000).toFixed(1)}s / {(data.latencyMs.p95 / 1000).toFixed(1)}s
          </strong>
        </div>
        <div className="admin-stat admin-stat-pending">
          <span>👍 / 👎</span>
          <strong className="admin-big-number">
            {data.feedback.up} / {data.feedback.down}
          </strong>
          <span>{percent(data.feedback.up, votes)} hài lòng</span>
        </div>
      </div>
      <div className="kb-editor-grid">
        <section className="admin-card admin-card-pad">
          <h2 className="kb-side-title">Kết quả trả lời</h2>
          <ul className="kb-bars">
            {Object.entries(data.grounded).map(([key, value]) => (
              <li key={key}>
                <span>{groundedLabels[key] ?? key}</span>
                <span className="admin-meter">
                  <span style={{ width: percent(value, data.turns) }} />
                </span>
                <span>{value}</span>
              </li>
            ))}
          </ul>
          <p className="kb-muted">
            Token trung bình mỗi lượt: {Math.round(data.tokensPerTurn).toLocaleString('vi-VN')}
          </p>
        </section>
        <section className="admin-card admin-card-pad">
          <h2 className="kb-side-title">Lý do không trả lời đầy đủ</h2>
          <ul className="kb-bars">
            {Object.entries(data.fallbackReasons).map(([key, value]) => (
              <li key={key}>
                <span>{fallbackLabels[key] ?? key}</span>
                <span className="admin-meter">
                  <span style={{ width: percent(value, data.turns) }} />
                </span>
                <span>{value}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
