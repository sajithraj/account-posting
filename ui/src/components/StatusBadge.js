import { jsx as _jsx } from "react/jsx-runtime";
const colours = {
    PNDG: { bg: '#fff3cd', text: '#856404' },
    ACSP: { bg: '#d1e7dd', text: '#0a3622' },
    RJCT: { bg: '#f8d7da', text: '#58151c' },
};
export default function StatusBadge({ status }) {
    const { bg, text } = colours[status] ?? { bg: '#e9ecef', text: '#495057' };
    return (_jsx("span", { style: { padding: '2px 8px', borderRadius: 12, fontSize: 12, fontWeight: 600, backgroundColor: bg, color: text }, children: status }));
}
