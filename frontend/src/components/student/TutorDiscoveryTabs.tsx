import { NavLink } from 'react-router-dom';
import './tutor-discovery-tabs.css';

export function TutorDiscoveryTabs() {
  return (
    <nav className="tutor-discovery-tabs" aria-label="Khám phá gia sư">
      <NavLink to="/student/teaching-requests" end>
        Tìm kiếm gia sư
      </NavLink>
      <NavLink to="/student/tutor-recommendations">Gợi ý gia sư</NavLink>
    </nav>
  );
}
