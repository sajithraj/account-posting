import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { BrowserRouter, Route, Routes, Link, useLocation } from 'react-router-dom';
import PostingListPage from './pages/PostingListPage';
import PostingDetailPage from './pages/PostingDetailPage';
import ConfigPage from './pages/ConfigPage';
import DashboardPage from './pages/DashboardPage';
function NavLink({ to, children }) {
    const { pathname } = useLocation();
    const active = pathname === to;
    return (_jsx(Link, { to: to, style: {
            color: active ? 'white' : '#90caf9',
            fontWeight: active ? 700 : 500,
            textDecoration: 'none',
            fontSize: 14,
            borderBottom: active ? '2px solid white' : '2px solid transparent',
            paddingBottom: 2,
        }, children: children }));
}
export default function App() {
    return (_jsxs(BrowserRouter, { children: [_jsxs("nav", { style: { padding: '10px 24px', background: '#003b5c', display: 'flex', alignItems: 'center', gap: 24 }, children: [_jsx(NavLink, { to: "/dashboard", children: "Dashboard" }), _jsx(NavLink, { to: "/", children: "Search" }), _jsx(NavLink, { to: "/config", children: "Config" })] }), _jsx("main", { style: { padding: '24px' }, children: _jsxs(Routes, { children: [_jsx(Route, { path: "/dashboard", element: _jsx(DashboardPage, {}) }), _jsx(Route, { path: "/", element: _jsx(PostingListPage, {}) }), _jsx(Route, { path: "/postings/:postingId", element: _jsx(PostingDetailPage, {}) }), _jsx(Route, { path: "/config", element: _jsx(ConfigPage, {}) })] }) })] }));
}
