import { useCallback, useState } from 'react';
import { useResource } from '../../hooks/useResource';
import { getRecommendationWeights, type RecommendationWeightModel } from '../../services/adminRecommendationWeightApi';
import { formatDateTime } from './adminFormat';
import { AdminState } from './components/AdminUi';
import './recommendation-weights.css';

const modelNames: Record<string, string> = {
  A_S2T: 'Mức phù hợp gia sư → học viên',
  A_T2S: 'Mức phù hợp học viên → gia sư',
  B_S2T: 'Khả năng gia sư nhận lời',
  B_T2S: 'Khả năng học viên chấp nhận'
};

const featureNames: Record<string, string> = {
  sem: 'Nội dung tương đồng', need: 'Nhu cầu', price: 'Học phí', style: 'Phong cách',
  sched: 'Lịch học', area: 'Khu vực', segment: 'Đối tượng', hist: 'Lịch sử',
  fitSegment: 'Hợp đối tượng', fitSched: 'Hợp lịch', fitPrice: 'Hợp học phí',
  fitQuality: 'Chất lượng', fitStyle: 'Hợp phong cách'
};

const number = (value: number | undefined) => value === undefined ? '—' : new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 }).format(value);

function WeightCard({ item }: { item: RecommendationWeightModel }) {
  const active = item.versions.find((version) => version.version === item.activeVersion);
  const latest = item.versions[0];
  const usingLearned = item.mode === 'ACTIVE' && Boolean(active);
  const weights = usingLearned ? active!.weights : item.prior;
  const status = usingLearned ? `Đang dùng bản học v${active!.version}` : 'Đang dùng trọng số gốc';

  return (
    <section className="admin-card rec-weights-card">
      <div className="admin-card-toolbar">
        <div className="rec-weights-model"><h2>{modelNames[item.model] ?? item.model}</h2><span>{item.model}</span></div>
        <span className={`admin-badge ${usingLearned ? 'admin-badge-success' : 'admin-badge-neutral'}`}>{status}</span>
      </div>
      <div className="admin-card-pad">
        <div className="admin-key-values">
          <div><span>Chế độ</span><strong>{item.mode === 'ACTIVE' ? 'Đang áp dụng' : item.mode === 'SHADOW' ? 'Học thử' : 'Tắt'}</strong></div>
          <div><span>Phiên bản áp dụng</span><strong>{usingLearned ? `v${active!.version}` : 'Gốc'}</strong></div>
          <div><span>Cập nhật bản đang chọn</span><strong>{active ? formatDateTime(active.createdAt) : 'Chưa có'}</strong></div>
          <div><span>Lần tạo phiên bản học gần nhất</span><strong>{latest ? formatDateTime(latest.createdAt) : 'Chưa có'}</strong></div>
        </div>
        {item.mode === 'SHADOW' && <div className="admin-alert admin-alert-muted">Đang học thử; kết quả tìm kiếm vẫn dùng trọng số gốc.</div>}
      </div>
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead><tr><th>Yếu tố</th><th>Trọng số đang dùng</th><th>Trọng số gốc</th></tr></thead>
          <tbody>{item.features.map((feature) => (
            <tr key={feature}><td>{featureNames[feature] ?? feature} <small>({feature})</small></td><td>{number(weights[feature])}</td><td>{number(item.prior[feature])}</td></tr>
          ))}</tbody>
        </table>
      </div>
      <details className="rec-weights-history">
        <summary>Lịch sử phiên bản ({item.versions.length})</summary>
        {item.versions.length ? (
          <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Phiên bản</th><th>Tạo lúc</th><th>Dữ liệu học đến</th><th>Số mẫu</th><th>Trạng thái</th></tr></thead><tbody>
            {item.versions.map((version) => <tr key={version.version}><td>v{version.version}</td><td>{formatDateTime(version.createdAt)}</td><td>{formatDateTime(version.trainedUntil)}</td><td>{version.sampleCount}</td><td>{version.active ? 'Được chọn' : 'Lưu trữ'}</td></tr>)}
          </tbody></table></div>
        ) : <p>Chưa có lần học nào tạo ra phiên bản trọng số.</p>}
      </details>
    </section>
  );
}

export function AdminRecommendationWeightsPage() {
  const [revision, setRevision] = useState(0);
  const load = useCallback((signal: AbortSignal) => getRecommendationWeights(signal), []);
  const { data, error, isLoading } = useResource(load, revision);
  return (
    <div className="admin-page rec-weights-page">
      <div className="admin-section-heading"><h1 className="admin-title">Trọng số gợi ý</h1><button className="admin-button secondary" type="button" onClick={() => setRevision((value) => value + 1)}>Làm mới</button></div>
      <p className="rec-weights-intro">Trọng số đang dùng và lịch sử tự học cho hai chiều ghép đôi.</p>
      {error && <AdminState error={error} />}
      {isLoading && !data && <AdminState loading />}
      {data?.models.map((item) => <WeightCard key={item.model} item={item} />)}
    </div>
  );
}
