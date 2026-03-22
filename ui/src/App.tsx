import { BrowserRouter, Route, Routes, Link, useLocation } from 'react-router-dom';
import PostingListPage from './pages/PostingListPage';
import PostingDetailPage from './pages/PostingDetailPage';
import CreatePostingPage from './pages/CreatePostingPage';
import ConfigPage from './pages/ConfigPage';
import DashboardPage from './pages/DashboardPage';

function NavLink({ to, children }: { to: string; children: React.ReactNode }) {
  const { pathname } = useLocation();
  const active = pathname === to;
  return (
    <Link
      to={to}
      style={{
        color: active ? 'white' : '#90caf9',
        fontWeight: active ? 700 : 500,
        textDecoration: 'none',
        fontSize: 14,
        borderBottom: active ? '2px solid white' : '2px solid transparent',
        paddingBottom: 2,
      }}
    >
      {children}
    </Link>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <nav style={{ padding: '10px 24px', background: '#003b5c', display: 'flex', alignItems: 'center', gap: 24 }}>
        <NavLink to="/dashboard">Dashboard</NavLink>
        <NavLink to="/">Search</NavLink>
        <NavLink to="/config">Config</NavLink>
      </nav>

      <main style={{ padding: '24px' }}>
        <Routes>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/" element={<PostingListPage />} />
          <Route path="/postings/:postingId" element={<PostingDetailPage />} />
          <Route path="/config" element={<ConfigPage />} />
        </Routes>
      </main>
    </BrowserRouter>
  );
}
