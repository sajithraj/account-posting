import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage, postingApi } from '../api/postingApi';
import StatusBadge from '../components/StatusBadge';
import LegTable from '../components/LegTable';
export default function PostingListPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [searchRequest, setSearchRequest] = useState({ limit: 20 });
    const [draft, setDraft] = useState({});
    const [selected, setSelected] = useState(new Set());
    const { data, isLoading, isError } = useQuery({
        queryKey: ['postings', searchRequest],
        queryFn: () => postingApi.search(searchRequest),
    });
    const retryAllMutation = useMutation({
        mutationFn: () => postingApi.retry(),
        onSuccess: () => {
            alert('Retry triggered successfully');
            queryClient.invalidateQueries({ queryKey: ['postings'] });
        },
        onError: (err) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });
    const retrySelectedMutation = useMutation({
        mutationFn: (ids) => postingApi.retry(ids),
        onSuccess: () => {
            setSelected(new Set());
            queryClient.invalidateQueries({ queryKey: ['postings'] });
        },
        onError: (err) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });
    const handleSearch = (e) => {
        e.preventDefault();
        const req = { limit: 50 };
        if (draft.endToEndReferenceId)
            req.endToEndReferenceId = draft.endToEndReferenceId;
        if (draft.sourceReferenceId)
            req.sourceReferenceId = draft.sourceReferenceId;
        if (draft.sourceName)
            req.sourceName = draft.sourceName;
        if (draft.status)
            req.status = draft.status;
        if (draft.requestType)
            req.requestType = draft.requestType;
        if (draft.fromDate)
            req.fromDate = draft.fromDate;
        if (draft.toDate)
            req.toDate = draft.toDate;
        setSearchRequest(req);
    };
    const items = data ?? [];
    const pendingIds = items.filter(p => p.postingStatus === 'PNDG').map(p => p.postingId);
    const hasPending = pendingIds.length > 0;
    const selectedPendingIds = [...selected].filter(id => items.some(p => p.postingId === id && p.postingStatus === 'PNDG'));
    const hasSelectedPending = selectedPendingIds.length > 0;
    const [expandedIds, setExpandedIds] = useState(new Set());
    const toggleExpand = (id, e) => {
        e.stopPropagation();
        setExpandedIds(prev => {
            const next = new Set(prev);
            if (next.has(id))
                next.delete(id);
            else
                next.add(id);
            return next;
        });
    };
    const toggleRow = (id) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(id))
                next.delete(id);
            else
                next.add(id);
            return next;
        });
    };
    const toggleAll = () => {
        const allIds = items.map(p => p.postingId);
        const allSelected = allIds.every(id => selected.has(id));
        setSelected(allSelected ? new Set() : new Set(allIds));
    };
    return (_jsxs("div", { style: s.page, children: [_jsxs("div", { style: s.header, children: [_jsx("h2", { style: s.title, children: "Account Posting Search" }), _jsxs("div", { style: s.headerActions, children: [_jsx("button", { style: {
                                    ...s.outlineBtn,
                                    ...(Object.values(draft).every(v => v === undefined || v === '') ? s.disabledBtn : {}),
                                }, onClick: () => {
                                    setDraft({});
                                    setSearchRequest({ limit: 20 });
                                }, disabled: Object.values(draft).every(v => v === undefined || v === ''), children: "\u2715 CLEAR FILTERS" }), _jsx("button", { style: { ...s.outlineBtn, ...(hasSelectedPending ? {} : s.disabledBtn) }, onClick: () => retrySelectedMutation.mutate(selectedPendingIds), disabled: !hasSelectedPending || retrySelectedMutation.isPending, children: retrySelectedMutation.isPending ? 'Processing...' : '⟳ RETRY SELECTED' }), _jsx("button", { style: { ...s.solidBtn, ...(!hasPending || retryAllMutation.isPending ? s.disabledBtn : {}) }, onClick: () => retryAllMutation.mutate(), disabled: !hasPending || retryAllMutation.isPending, children: retryAllMutation.isPending ? 'Processing...' : '⟳ RETRY ALL PENDING' })] })] }), _jsxs("form", { onSubmit: handleSearch, style: s.filterBar, children: [_jsx("input", { style: s.filterInput, placeholder: "End to End Reference", value: draft.endToEndReferenceId ?? '', onChange: e => setDraft(d => ({ ...d, endToEndReferenceId: e.target.value || undefined })) }), _jsx("input", { style: s.filterInput, placeholder: "Source Reference", value: draft.sourceReferenceId ?? '', onChange: e => setDraft(d => ({ ...d, sourceReferenceId: e.target.value || undefined })) }), _jsxs("select", { style: s.filterSelect, value: draft.sourceName ?? '', onChange: e => setDraft(d => ({ ...d, sourceName: e.target.value || undefined })), children: [_jsx("option", { value: "", children: "Source Name" }), _jsx("option", { value: "IMX", children: "IMX" }), _jsx("option", { value: "RMS", children: "RMS" }), _jsx("option", { value: "STABLECOIN", children: "STABLECOIN" })] }), _jsxs("select", { style: s.filterSelect, value: draft.status ?? '', onChange: e => setDraft(d => ({ ...d, status: e.target.value || undefined })), children: [_jsx("option", { value: "", children: "Posting Status" }), _jsx("option", { value: "PNDG", children: "PNDG" }), _jsx("option", { value: "ACSP", children: "ACSP" }), _jsx("option", { value: "RCVD", children: "RCVD" }), _jsx("option", { value: "RJCT", children: "RJCT" })] }), _jsxs("select", { style: s.filterSelect, value: draft.requestType ?? '', onChange: e => setDraft(d => ({ ...d, requestType: e.target.value || undefined })), children: [_jsx("option", { value: "", children: "Request Type" }), _jsx("option", { value: "IMX_OBPM", children: "IMX_OBPM" }), _jsx("option", { value: "IMX_CBS_GL", children: "IMX_CBS_GL" }), _jsx("option", { value: "FED_RETURN", children: "FED_RETURN" }), _jsx("option", { value: "GL_RETURN", children: "GL_RETURN" }), _jsx("option", { value: "MCA_RETURN", children: "MCA_RETURN" }), _jsx("option", { value: "BUY_CUSTOMER_POSTING", children: "BUY_CUSTOMER_POSTING" }), _jsx("option", { value: "ADD_ACCOUNT_HOLD", children: "ADD_ACCOUNT_HOLD" }), _jsx("option", { value: "CUSTOMER_POSTING", children: "CUSTOMER_POSTING" })] }), _jsxs("div", { style: s.dateRange, children: [_jsx("input", { type: "date", style: s.filterInput, value: draft.fromDate ?? '', onChange: e => setDraft(d => ({ ...d, fromDate: e.target.value || undefined })) }), _jsx("span", { style: s.dateSep, children: "\u2013" }), _jsx("input", { type: "date", style: s.filterInput, value: draft.toDate ?? '', onChange: e => setDraft(d => ({ ...d, toDate: e.target.value || undefined })) })] }), _jsx("button", { type: "submit", style: s.searchIconBtn, title: "Search", children: "\uD83D\uDD0D" })] }), isLoading && _jsx("div", { style: s.statusMsg, children: "Loading..." }), isError && _jsx("div", { style: { ...s.statusMsg, color: '#c0392b' }, children: "Failed to load postings." }), data && (_jsxs(_Fragment, { children: [_jsxs("div", { style: s.resultCount, children: [items.length, " result", items.length !== 1 ? 's' : ''] }), _jsxs("table", { style: s.table, children: [_jsx("thead", { children: _jsxs("tr", { style: s.theadRow, children: [_jsx("th", { style: { ...s.th, width: 32 }, children: _jsx("input", { type: "checkbox", checked: items.length > 0 && items.every(p => selected.has(p.postingId)), onChange: toggleAll }) }), _jsx("th", { style: s.th, children: "Reference Number" }), _jsx("th", { style: s.th, children: "Source Reference" }), _jsx("th", { style: s.th, children: "Source Name" }), _jsx("th", { style: s.th, children: "Request Type" }), _jsx("th", { style: s.th, children: "Target Systems" }), _jsx("th", { style: s.th, children: "Exec. Date" }), _jsx("th", { style: s.th, children: "Amount" }), _jsx("th", { style: s.th, children: "Currency" }), _jsx("th", { style: s.th, children: "Payment Status" }), _jsx("th", { style: s.th, children: "Reason" }), _jsx("th", { style: { ...s.th, width: 48 } })] }) }), _jsx("tbody", { children: items.map(p => {
                                    const expanded = expandedIds.has(p.postingId);
                                    return (_jsxs(Fragment, { children: [_jsxs("tr", { onClick: () => navigate(`/postings/${p.postingId}`), style: { ...s.tbodyRow, background: expanded ? '#eef2ff' : 'white' }, onMouseEnter: e => (e.currentTarget.style.background = '#f5f8ff'), onMouseLeave: e => (e.currentTarget.style.background = expanded ? '#eef2ff' : 'white'), children: [_jsx("td", { style: { ...s.td, width: 32 }, onClick: e => e.stopPropagation(), children: _jsx("input", { type: "checkbox", checked: selected.has(p.postingId), onChange: () => toggleRow(p.postingId) }) }), _jsx("td", { style: { ...s.td, ...s.linkCell }, children: p.endToEndReferenceId }), _jsx("td", { style: s.td, children: p.sourceReferenceId }), _jsx("td", { style: s.td, children: p.sourceName }), _jsx("td", { style: s.td, children: p.requestType }), _jsx("td", { style: s.td, children: p.targetSystems ?? '—' }), _jsx("td", { style: s.td, children: p.requestedExecutionDate }), _jsx("td", { style: s.td, children: p.amount }), _jsx("td", { style: s.td, children: p.currency }), _jsx("td", { style: s.td, children: _jsx(StatusBadge, { status: p.postingStatus }) }), _jsx("td", { style: { ...s.td, ...s.reasonCell }, title: p.reason ?? '', children: p.reason ?? '—' }), _jsx("td", { style: { ...s.td, width: 48 }, onClick: e => e.stopPropagation(), children: _jsx("div", { style: { display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }, children: _jsx("button", { style: { ...s.expandBtn, ...(expanded ? s.expandBtnActive : {}) }, onClick: e => toggleExpand(p.postingId, e), title: expanded ? 'Collapse legs' : 'Expand legs', children: expanded ? '▲' : '▼' }) }) })] }), expanded && (_jsx("tr", { style: { background: '#f4f7ff' }, children: _jsxs("td", { colSpan: 12, style: s.expandedCell, children: [_jsx("div", { style: s.expandedLabel, children: "Posting Legs" }), _jsx(LegTable, { legs: p.legs ?? [] })] }) }))] }, p.postingId));
                                }) })] })] }))] }));
}
const s = {
    page: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif', color: '#222' },
    header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
    title: { margin: 0, fontSize: 22, fontWeight: 600, color: '#1a2a3a' },
    headerActions: { display: 'flex', gap: 8 },
    outlineBtn: {
        padding: '7px 14px', border: '1px solid #003b5c', borderRadius: 4,
        background: 'white', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#003b5c',
    },
    solidBtn: {
        padding: '7px 14px', border: 'none', borderRadius: 4,
        background: '#003b5c', color: 'white', cursor: 'pointer', fontSize: 13, fontWeight: 500,
    },
    disabledBtn: { opacity: 0.4, cursor: 'not-allowed' },
    filterBar: {
        display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center',
        padding: '12px 16px', background: '#f4f6f9', border: '1px solid #dde2ea',
        borderRadius: 6, marginBottom: 16,
    },
    filterInput: {
        padding: '5px 10px', border: '1px solid #c5cdd8', borderRadius: 4,
        fontSize: 13, height: 30, outline: 'none', background: 'white',
    },
    filterSelect: {
        padding: '5px 8px', border: '1px solid #c5cdd8', borderRadius: 4,
        fontSize: 13, height: 30, background: 'white', cursor: 'pointer',
    },
    dateRange: { display: 'flex', alignItems: 'center', gap: 4 },
    dateSep: { color: '#888', fontSize: 14 },
    searchIconBtn: {
        padding: '5px 12px', background: '#003b5c', color: 'white', border: 'none',
        borderRadius: 4, cursor: 'pointer', fontSize: 15, height: 30,
    },
    statusMsg: { padding: 24, textAlign: 'center', color: '#666' },
    resultCount: { fontSize: 12, color: '#888', marginBottom: 6 },
    table: {
        width: '100%', borderCollapse: 'collapse', fontSize: 13,
        border: '1px solid #dde2ea', borderRadius: 4, overflow: 'hidden',
    },
    theadRow: { background: '#eef1f5', borderBottom: '2px solid #c5cdd8' },
    th: {
        padding: '10px 12px', textAlign: 'left', fontWeight: 600,
        color: '#444', whiteSpace: 'nowrap', borderBottom: '1px solid #dde2ea',
    },
    tbodyRow: { background: 'white', cursor: 'pointer', transition: 'background 0.1s' },
    td: { padding: '10px 12px', borderBottom: '1px solid #eef1f5', verticalAlign: 'middle' },
    linkCell: { color: '#0072ce', fontWeight: 500 },
    expandBtn: {
        padding: '3px 8px', border: '1px solid #c5cdd8', borderRadius: 4,
        background: 'white', color: '#555', cursor: 'pointer', fontSize: 10, lineHeight: 1,
    },
    expandBtnActive: { background: '#e8eeff', borderColor: '#7b9ef0', color: '#1a3fa8' },
    expandedCell: { padding: '12px 20px 16px', borderBottom: '2px solid #c5cdd8' },
    expandedLabel: {
        fontSize: 11, fontWeight: 600, color: '#666',
        textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 8,
    },
    reasonCell: {
        maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis',
        whiteSpace: 'nowrap', color: '#555', fontSize: 12,
    },
};
