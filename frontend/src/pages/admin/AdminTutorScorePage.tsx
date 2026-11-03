import { useCallback } from 'react';
import { useResource } from '../../hooks/useResource';
import { getAdminTutorScore } from '../../services/adminTutorApi';
import { formatDateTime, formatNumber } from './adminFormat';
import { useAdminTutor } from './adminTutorContext';
import { AdminState } from './components/AdminUi';

/** Maximum points per component, mirroring TutorRecommendationScoreService. */
const components = [
  { key: 'rating', label: 'Đánh giá', hint: 'điểm TB có trọng số theo số lượt', max: 35 },
  { key: 'completion', label: 'Hoàn thành hợp đồng', hint: 'tối đa ở 20 hợp đồng', max: 20 },
  { key: 'acceptance', label: 'Tỉ lệ nhận yêu cầu', hint: 'theo % chấp nhận', max: 15 },
  { key: 'response', label: 'Tốc độ phản hồi', hint: '≤1 giờ đạt tối đa, ≥48 giờ = 0', max: 10 },
  { key: 'experience', label: 'Kinh nghiệm', hint: 'tối đa ở 20 học viên', max: 10 },
  { key: 'verification', label: 'Xác minh', hint: 'hồ sơ đã duyệt +5, có chứng chỉ +5', max: 10 }
];
const MAX_PENALTY = 15;

export function AdminTutorScorePage() {
  const { tutor } = useAdminTutor();
  const load = useCallback((signal: AbortSignal) => getAdminTutorScore(tutor.userId, signal), [tutor.userId]);
  const { data: score, error, isLoading } = useResource(load);

  if (!score) return <AdminState loading={isLoading} error={error} />;
  const breakdown = score.breakdown ?? {};
  const penalty = Number(breakdown.violationPenalty ?? 0);
  const percent = (value: number, max: number) => `${Math.max(0, Math.min(100, (value / max) * 100))}%`;

  return (
    <div className="admin-columns">
      <section className="admin-card admin-card-pad admin-column-side admin-column-first">
        <h2>Điểm đề xuất</h2>
        <div className="admin-big-number">
          <strong className="admin-text-brand">{formatNumber(score.recommendationScore)}</strong>
          <span>/ 100</span>
        </div>
        <div className="admin-key-values">
          <div>
            <span>Công thức</span>
            <strong>{score.formulaVersion ?? '—'}</strong>
          </div>
          <div>
            <span>Cập nhật</span>
            <strong>{formatDateTime(score.updatedAt)}</strong>
          </div>
          <div>
            <span>Chứng chỉ đã xác minh</span>
            <strong>{breakdown.verifiedCertificates ?? 0}</strong>
          </div>
        </div>
      </section>
      <section className="admin-card admin-card-pad admin-column-main">
        <h2>Chi tiết từng thành phần</h2>
        {score.breakdown ? (
          <div className="admin-score-list">
            {components.map((component) => {
              const value = Number(breakdown[component.key] ?? 0);
              return (
                <div key={component.key} className="admin-score-row">
                  <div>
                    <span>
                      <strong>{component.label}</strong> · {component.hint}
                    </span>
                    <strong>
                      {formatNumber(value)} / {component.max}
                    </strong>
                  </div>
                  <div className="admin-meter" aria-hidden="true">
                    <span style={{ width: percent(value, component.max) }} />
                  </div>
                </div>
              );
            })}
            <div className="admin-score-row admin-score-penalty">
              <div>
                <span>
                  <strong>Trừ điểm vi phạm</strong> · 5 điểm mỗi lần đình chỉ, tối đa {MAX_PENALTY}
                </span>
                <strong>
                  −{formatNumber(penalty)} / {MAX_PENALTY}
                </strong>
              </div>
              <div className="admin-meter" aria-hidden="true">
                <span style={{ width: percent(penalty, MAX_PENALTY) }} />
              </div>
            </div>
          </div>
        ) : (
          <AdminState empty="Chưa có chi tiết điểm." />
        )}
      </section>
    </div>
  );
}
