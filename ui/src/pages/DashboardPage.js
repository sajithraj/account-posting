import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { postingApi } from '../api/postingApi';
const SOURCE_COLORS = [
    '#2d3a8c', '#1a5c4a', '#6b2d8c', '#7a3d1a',
    '#1a4a6b', '#5c3a1a', '#2d6b4a', '#8c2d4a',
];
function getPresetRange(preset) {
    const now = new Date();
    const fmt = (d) => d.toISOString().slice(0, 10);
    const today = fmt(now);
    if (preset === 'today')
        return { fromDate: today, toDate: today };
    if (preset === 'this_week') {
        const mon = new Date(now);
        mon.setDate(now.getDate() - ((now.getDay() + 6) % 7));
        return { fromDate: fmt(mon), toDate: today };
    }
    if (preset === 'this_month')
        return { fromDate: fmt(new Date(now.getFullYear(), now.getMonth(), 1)), toDate: today };
    if (preset === 'this_quarter')
        return {
            fromDate: fmt(new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1)),
            toDate: today,
        };
    if (preset === 'this_year')
        return { fromDate: fmt(new Date(now.getFullYear(), 0, 1)), toDate: today };
    return {};
}
export default function DashboardPage() {
    const [preset, setPreset] = useState('this_month');
    const [customFrom, setCustomFrom] = useState('');
    const [customTo, setCustomTo] = useState('');
    const range = preset === 'custom'
        ? { fromDate: customFrom || undefined, toDate: customTo || undefined }
        : getPresetRange(preset);
    const { data: configs } = useQuery({
        queryKey: ['configs'],
        queryFn: postingApi.getAllConfigs,
        staleTime: 5 * 60000,
    });
    const sourceSystems = [...new Set((configs ?? []).map(c => c.sourceName))].sort();
    const sourcePanels = sourceSystems.map((name, i) => ({ name, color: SOURCE_COLORS[i % SOURCE_COLORS.length] }));
    return (_jsxs("div", { style: s.page, children: [_jsxs("div", { style: s.header, children: [_jsx("h2", { style: s.title, children: "Dashboard" }), _jsxs("div", { style: s.rangeBar, children: [_jsxs("select", { style: s.rangeSelect, value: preset, onChange: e => setPreset(e.target.value), children: [_jsx("option", { value: "today", children: "Today" }), _jsx("option", { value: "this_week", children: "This Week" }), _jsx("option", { value: "this_month", children: "This Month" }), _jsx("option", { value: "this_quarter", children: "This Quarter" }), _jsx("option", { value: "this_year", children: "This Year" }), _jsx("option", { value: "custom", children: "Custom Range" })] }), preset === 'custom' && (_jsxs(_Fragment, { children: [_jsx("input", { type: "date", style: s.dateInput, value: customFrom, onChange: e => setCustomFrom(e.target.value) }), _jsx("span", { style: s.dateSep, children: "\u2013" }), _jsx("input", { type: "date", style: s.dateInput, value: customTo, onChange: e => setCustomTo(e.target.value) })] })), preset !== 'custom' && range.fromDate && (_jsxs("span", { style: s.rangeLabel, children: [range.fromDate, " \u2013 ", range.toDate] }))] })] }), _jsx("div", { style: s.sectionHeader, children: "By Source System" }), sourcePanels.length === 0
                ? _jsx("div", { style: s.empty, children: "Loading source systems\u2026" })
                : (_jsx("div", { style: s.grid, children: sourcePanels.map(p => (_jsx(SourcePanel, { name: p.name, color: p.color, range: range }, p.name))) }))] }));
}
function SourcePanel({ name, color, range }) {
    const base = { sourceName: name, fromDate: range.fromDate, toDate: range.toDate, limit: 100 };
    const total = useCount({ ...base });
    const pending = useCount({ ...base, status: 'PNDG' });
    const accepted = useCount({ ...base, status: 'ACSP' });
    const rejected = useCount({ ...base, status: 'RJCT' });
    return (_jsxs("div", { style: s.panel, children: [_jsxs("div", { style: { ...s.panelHeader, background: color }, children: [_jsx("span", { style: s.panelTitle, children: name }), _jsxs("span", { style: s.panelTotal, children: [total ?? '…', " total"] })] }), _jsxs("div", { style: s.counters, children: [_jsx(Counter, { label: "PNDG", value: pending, color: "#856404", bg: "#fffbeb" }), _jsx(Counter, { label: "ACSP", value: accepted, color: "#0a3622", bg: "#f0fdf4" }), _jsx(Counter, { label: "RJCT", value: rejected, color: "#58151c", bg: "#fff1f2" })] })] }));
}
function useCount(params) {
    const { data } = useQuery({
        queryKey: ['dashboard-count', params],
        queryFn: () => postingApi.search(params),
        staleTime: 30000,
    });
    return data?.length;
}
function Counter({ label, value, color, bg }) {
    return (_jsxs("div", { style: { ...s.counter, background: bg, color }, children: [_jsx("div", { style: s.counterValue, children: value ?? '—' }), _jsx("div", { style: s.counterLabel, children: label })] }));
}
const s = {
    page: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif', color: '#222' },
    header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, flexWrap: 'wrap', gap: 12 },
    title: { margin: 0, fontSize: 22, fontWeight: 600, color: '#1a2a3a' },
    sectionHeader: {
        fontSize: 13, fontWeight: 700, color: '#444', textTransform: 'uppercase',
        letterSpacing: 0.8, marginBottom: 14, paddingBottom: 6, borderBottom: '2px solid #dde2ea',
    },
    rangeBar: { display: 'flex', alignItems: 'center', gap: 8 },
    rangeSelect: { padding: '6px 10px', border: '1px solid #c5cdd8', borderRadius: 4, fontSize: 13, background: 'white', cursor: 'pointer' },
    dateInput: { padding: '5px 10px', border: '1px solid #c5cdd8', borderRadius: 4, fontSize: 13, background: 'white' },
    dateSep: { color: '#888', fontSize: 14 },
    rangeLabel: { fontSize: 12, color: '#666', background: '#f4f6f9', border: '1px solid #dde2ea', borderRadius: 4, padding: '4px 10px' },
    grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 20 },
    panel: { border: '1px solid #dde2ea', borderRadius: 8, overflow: 'hidden', background: 'white' },
    panelHeader: { padding: '14px 16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' },
    panelTitle: { color: 'white', fontWeight: 700, fontSize: 16, letterSpacing: 0.5 },
    panelTotal: { color: 'rgba(255,255,255,0.75)', fontSize: 12 },
    counters: { display: 'grid', gridTemplateColumns: '1fr 1fr 1fr' },
    counter: { padding: '14px 8px', textAlign: 'center', borderRight: '1px solid #eef1f5' },
    counterValue: { fontSize: 26, fontWeight: 700, lineHeight: 1 },
    counterLabel: { fontSize: 10, fontWeight: 600, marginTop: 4, letterSpacing: 0.5 },
    empty: { textAlign: 'center', padding: 40, color: '#888', fontSize: 14 },
};
