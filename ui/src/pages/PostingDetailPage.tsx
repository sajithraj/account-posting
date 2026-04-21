import {useState} from 'react';
import {useNavigate, useParams} from 'react-router-dom';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {getErrorMessage, postingApi} from '../api/postingApi';
import StatusBadge from '../components/StatusBadge';
import LegTable from '../components/LegTable';

export default function PostingDetailPage() {
    const {postingId} = useParams<{ postingId: string }>();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const id = Number(postingId);

    const [updatingLegOrder, setUpdatingLegOrder] = useState<number | null>(null);

    const {data: posting, isLoading, isError} = useQuery({
        queryKey: ['posting', postingId],
        queryFn: () => postingApi.getById(id),
        enabled: !!postingId,
    });

    const retryMutation = useMutation({
        mutationFn: () => postingApi.retry([id]),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['posting', postingId]});
            queryClient.invalidateQueries({queryKey: ['postings']});
        },
        onError: (err) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });

    const updateLegMutation = useMutation({
        mutationFn: ({transactionOrder, status, reason}: { transactionOrder: number; status: string; reason?: string }) =>
            postingApi.updateLegStatus(id, transactionOrder, status, reason),
        onSuccess: () => {
            setUpdatingLegOrder(null);
            queryClient.invalidateQueries({queryKey: ['posting', postingId]});
            queryClient.invalidateQueries({queryKey: ['postings']});
        },
        onError: (err) => {
            setUpdatingLegOrder(null);
            alert(`Status update failed: ${getErrorMessage(err)}`);
        },
    });

    if (isLoading) return <p>Loading...</p>;
    if (isError || !posting) return <p style={{color: 'red'}}>Posting not found.</p>;

    const canRetry = posting.postingStatus === 'PNDG' || posting.postingStatus === 'RECEIVED';

    const handleUpdateLegStatus = (transactionOrder: number, status: string, reason?: string) => {
        setUpdatingLegOrder(transactionOrder);
        updateLegMutation.mutate({transactionOrder, status, reason});
    };

    return (
        <div style={{fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'}}>
            <div style={{display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20}}>
                <button onClick={() => navigate(-1)} style={s.backBtn}>← Back</button>
                <h2 style={{margin: 0, fontSize: 20, fontWeight: 600}}>
                    Posting — {posting.endToEndReferenceId}
                </h2>
                {canRetry && (
                    <button
                        style={{...s.retryBtn, ...(retryMutation.isPending ? s.disabled : {})}}
                        onClick={() => retryMutation.mutate()}
                        disabled={retryMutation.isPending}
                    >
                        {retryMutation.isPending ? 'Retrying...' : '⟳ Retry Failed Legs'}
                    </button>
                )}
            </div>

            {retryMutation.isSuccess && (
                <div style={s.successBanner}>Retry completed — legs updated.</div>
            )}
            {updateLegMutation.isSuccess && (
                <div style={s.successBanner}>Leg status updated successfully.</div>
            )}

            <section style={s.grid}>
                <Field label="Status"><StatusBadge status={posting.postingStatus}/></Field>
                <Field label="Source">{posting.sourceName}</Field>
                <Field label="Source Reference">{posting.sourceReferenceId}</Field>
                <Field label="End-to-End Reference">{posting.endToEndReferenceId}</Field>
                <Field label="Request Type">{posting.requestType}</Field>
                <Field label="Amount">{posting.amount} {posting.currency} ({posting.creditDebitIndicator})</Field>
                <Field label="Debtor Account">{posting.debtorAccount}</Field>
                <Field label="Creditor Account">{posting.creditorAccount}</Field>
                <Field label="Execution Date">{posting.requestedExecutionDate}</Field>
                <Field label="Remittance">{posting.remittanceInformation ?? '—'}</Field>
                <Field label="Reason">{posting.reason ?? '—'}</Field>
                <Field label="Processed At">{posting.processedAt ?? '—'}</Field>
                <Field label="Created At">{posting.createdAt}</Field>
            </section>

            <h3 style={{marginTop: 28, marginBottom: 12, fontSize: 15, fontWeight: 600}}>Posting Legs</h3>
            <LegTable
                legs={posting.legs ?? []}
                onUpdateStatus={handleUpdateLegStatus}
                updatingLegOrder={updatingLegOrder}
            />
        </div>
    );
}

function Field({label, children}: { label: string; children: React.ReactNode }) {
    return (
        <div>
            <div style={{
                fontSize: 11, color: '#888', marginBottom: 2,
                textTransform: 'uppercase', letterSpacing: 0.5,
            }}>{label}</div>
            <div style={{fontWeight: 500}}>{children}</div>
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    backBtn: {
        padding: '5px 12px', background: 'white', border: '1px solid #c5cdd8',
        borderRadius: 4, cursor: 'pointer', fontSize: 13,
    },
    retryBtn: {
        padding: '6px 16px', background: '#003b5c', color: 'white', border: 'none',
        borderRadius: 4, cursor: 'pointer', fontSize: 13, fontWeight: 500, marginLeft: 'auto',
    },
    disabled: {opacity: 0.6, cursor: 'not-allowed'},
    successBanner: {
        marginBottom: 16, padding: '8px 14px', background: '#d1f0d8', color: '#0a3622',
        borderRadius: 4, fontSize: 13,
    },
    grid: {
        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16,
        padding: 20, background: '#f9fafb', borderRadius: 6, border: '1px solid #dde2ea',
    },
};
