import './App.css';
import HomePage from './pages/HomePage';
import AdminStatisticsPage from './pages/AdminStatisticsPage';

export default function App() {
  if (window.location.pathname.startsWith('/admin/statistics')) {
    return <AdminStatisticsPage />;
  }

  return <HomePage />;
}
