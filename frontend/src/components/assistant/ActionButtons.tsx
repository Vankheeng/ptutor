// Nút điều hướng dưới câu trả lời: chỉ route nội bộ, đi bằng react-router (không mở link ngoài) — 09 T11.
import { useNavigate } from 'react-router-dom';
import type { AssistantAction } from '../../types/assistant';

export function ActionButtons({ actions }: { actions: AssistantAction[] }) {
  const navigate = useNavigate();
  const internal = actions.filter((action) => action.route.startsWith('/') && !action.route.startsWith('//'));
  if (internal.length === 0) return null;
  return (
    <div className="assistant-actions">
      {internal.map((action) => (
        <button key={action.route + action.label} type="button" onClick={() => navigate(action.route)}>
          {action.label}
        </button>
      ))}
    </div>
  );
}
