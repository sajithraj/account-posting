import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { postingApi, getErrorMessage } from '../api/postingApi';
import StatusBadge from '../components/StatusBadge';
import LegTable from '../components/LegTable';
export default function PostingDetailPage() {
    const { postingId } = useParams();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const id = Number(postingId);
    const [updatingLegId, setUpdatingLegId] = useState(null);
    const { data: posting, isLoading, isError } = useQuery({
        queryKey: ['posting', postingId],
        queryFn: () => postingApi.getById(id),
        enabled: !!postingId,
    });
    const retryMutation = useMutation({
        mutationFn: () => postingApi.retry([id]),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['posting', postingId] });
            queryClient.invalidateQueries({ queryKey: ['postings'] });
        },
        onError: (err) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });
    const updateLegMutation = useMutation({
        mutationFn: ({ legId, status, reason }) => postingApi.updateLegStatus(id, legId, status, reason),
        onSuccess: () => {
            setUpdatingLegId(null);
            queryClient.invalidateQueries({ queryKey: ['posting', postingId] });
            queryClient.invalidateQueries({ queryKey: ['postings'] });
        },
        onError: (err) => {
            setUpdatingLegId(null);
            alert(`Status update failed: ${getErrorMessage(err)}`);
        },
    });
    if (isLoading)
        return _jsx("p", { children: "Loading..." });
    if (isError || !posting)
        return _jsx("p", { style: { color: 'red' }, children: "Posting not found." });
    const canRetry = posting.postingStatus === 'PNDG';
    const handleUpdateLegStatus = (legId, status, reason) => {
        setUpdatingLegId(legId);
        updateLegMutation.mutate({ legId, status, reason });
    };
    return (_jsxs("div", { style: { fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif' }, children: [_jsxs("div", { style: { display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }, children: [_jsx("button", { onClick: () => navigate(-1), style: s.backBtn, children: "\u2190 Back" }), _jsxs("h2", { style: { margin: 0, fontSize: 20, fontWeight: 600 }, children: ["Posting #", posting.postingId] }), canRetry && (_jsx("button", { style: { ...s.retryBtn, ...(retryMutation.isPending ? s.disabled : {}) }, onClick: () => retryMutation.mutate(), disabled: retryMutation.isPending, children: retryMutation.isPending ? 'Retrying...' : '⟳ Retry Failed Legs' }))] }), retryMutation.isSuccess && (_jsx("div", { style: s.successBanner, children: "Retry completed \u2014 legs updated." })), updateLegMutation.isSuccess && (_jsx("div", { style: s.successBanner, children: "Leg status updated successfully." })), _jsxs("section", { style: s.grid, children: [_jsx(Field, { label: "Status", children: _jsx(StatusBadge, { status: posting.postingStatus }) }), _jsx(Field, { label: "Source", children: posting.sourceName }), _jsx(Field, { label: "Source Reference", children: posting.sourceReferenceId }), _jsx(Field, { label: "End-to-End Reference", children: posting.endToEndReferenceId }), _jsx(Field, { label: "Request Type", children: posting.requestType }), _jsxs(Field, { label: "Amount", children: [posting.amount, " ", posting.currency, " (", posting.creditDebitIndicator, ")"] }), _jsx(Field, { label: "Debtor Account", children: posting.debtorAccount }), _jsx(Field, { label: "Creditor Account", children: posting.creditorAccount }), _jsx(Field, { label: "Execution Date", children: posting.requestedExecutionDate }), _jsx(Field, { label: "Remittance", children: posting.remittanceInformation ?? '—' }), _jsx(Field, { label: "Reason", children: posting.reason ?? '—' }), _jsx(Field, { label: "Processed At", children: posting.processedAt ?? '—' }), _jsx(Field, { label: "Created At", children: posting.createdAt })] }), _jsx("h3", { style: { marginTop: 28, marginBottom: 12, fontSize: 15, fontWeight: 600 }, children: "Posting Legs" }), _jsx(LegTable, { legs: posting.responses ?? [], onUpdateStatus: handleUpdateLegStatus, updatingLegId: updatingLegId })] }));
}
function Field({ label, children }) {
    return (_jsxs("div", { children: [_jsx("div", { style: { fontSize: 11, color: '#888', marginBottom: 2, textTransform: 'uppercase', letterSpacing: 0.5 }, children: label }), _jsx("div", { style: { fontWeight: 500 }, children: children })] }));
}
const s = {
    backBtn: {
        padding: '5px 12px', background: 'white', border: '1px solid #c5cdd8',
        borderRadius: 4, cursor: 'pointer', fontSize: 13,
    },
    retryBtn: {
        padding: '6px 16px', background: '#003b5c', color: 'white', border: 'none',
        borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500, marginLeft: 'auto',
    },
    disabled: { opacity: 0.6, cursor: 'not-allowed' },
    successBanner: {
        marginBottom: 16, padding: '8px 14px', background: '#d1f0d8', color: '#0a3622',
        borderRadius: 4, fontSize: 13,
    },
    grid: {
        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16,
        padding: 20, background: '#f9fafb', borderRadius: 6, border: '1px solid #dde2ea',
    },
};
