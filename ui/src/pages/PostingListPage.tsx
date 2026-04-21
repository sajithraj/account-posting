import {Fragment, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {getErrorMessage, postingApi} from '../api/postingApi';
import type {PostingFilterDraft, PostingSearchRequest, PostingStatus} from '../types/posting';
import StatusBadge from '../components/StatusBadge';
import LegTable from '../components/LegTable';

export default function PostingListPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const [searchRequest, setSearchRequest] = useState<PostingSearchRequest>({limit: 20});
    const [draft, setDraft] = useState<PostingFilterDraft>({});
    const [selected, setSelected] = useState<Set<string>>(new Set());

    const {data, isLoading, isError} = useQuery({
        queryKey: ['postings', searchRequest],
        queryFn: () => postingApi.search(searchRequest),
    });

    const retryAllMutation = useMutation({
        mutationFn: () => postingApi.retry(),
        onSuccess: () => {
            alert('Retry triggered successfully');
            queryClient.invalidateQueries({queryKey: ['postings']});
        },
        onError: (err: unknown) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });

    const retrySelectedMutation = useMutation({
        mutationFn: (ids: string[]) => postingApi.retry(ids),
        onSuccess: () => {
            setSelected(new Set());
            queryClient.invalidateQueries({queryKey: ['postings']});
        },
        onError: (err: unknown) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        const req: PostingSearchRequest = {limit: 50};
        if (draft.endToEndReferenceId) req.endToEndReferenceId = draft.endToEndReferenceId;
        if (draft.sourceReferenceId) req.sourceReferenceId = draft.sourceReferenceId;
        if (draft.sourceName) req.sourceName = draft.sourceName;
        if (draft.status) req.status = draft.status;
        if (draft.requestType) req.requestType = draft.requestType;
        if (draft.fromDate) req.fromDate = draft.fromDate;
        if (draft.toDate) req.toDate = draft.toDate;
        setSearchRequest(req);
    };

    const items = data ?? [];
    const pendingIds = items.filter(p => p.postingStatus === 'PNDG').map(p => p.postingId);
    const hasPending = pendingIds.length > 0;

    const selectedPendingIds = [...selected].filter(id =>
        items.some(p => p.postingId === id && p.postingStatus === 'PNDG'),
    );
    const hasSelectedPending = selectedPendingIds.length > 0;

    const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

    const toggleExpand = (id: string, e: React.MouseEvent) => {
        e.stopPropagation();
        setExpandedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const toggleRow = (id: string) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const toggleAll = () => {
        const allIds = items.map(p => p.postingId);
        const allSelected = allIds.every(id => selected.has(id));
        setSelected(allSelected ? new Set() : new Set(allIds));
    };

    return (
        <div style={s.page}>
            {/* Header */}
            <div style={s.header}>
                <h2 style={s.title}>Account Posting Search</h2>
                <div style={s.headerActions}>
                    <button
                        style={{
                            ...s.outlineBtn,
                            ...(Object.values(draft).every(v => v === undefined || v === '') ? s.disabledBtn : {}),
                        }}
                        onClick={() => {
                            setDraft({});
                            setSearchRequest({limit: 20});
                        }}
                        disabled={Object.values(draft).every(v => v === undefined || v === '')}
                    >
                        ✕ CLEAR FILTERS
                    </button>
                    <button
                        style={{...s.outlineBtn, ...(hasSelectedPending ? {} : s.disabledBtn)}}
                        onClick={() => retrySelectedMutation.mutate(selectedPendingIds)}
                        disabled={!hasSelectedPending || retrySelectedMutation.isPending}
                    >
                        {retrySelectedMutation.isPending ? 'Processing...' : '⟳ RETRY SELECTED'}
                    </button>
                    <button
                        style={{...s.solidBtn, ...(!hasPending || retryAllMutation.isPending ? s.disabledBtn : {})}}
                        onClick={() => retryAllMutation.mutate()}
                        disabled={!hasPending || retryAllMutation.isPending}
                    >
                        {retryAllMutation.isPending ? 'Processing...' : '⟳ RETRY ALL PENDING'}
                    </button>
                </div>
            </div>

            {/* Filter row */}
            <form onSubmit={handleSearch} style={s.filterBar}>
                <input
                    style={s.filterInput}
                    placeholder="End to End Reference"
                    value={draft.endToEndReferenceId ?? ''}
                    onChange={e => setDraft(d => ({...d, endToEndReferenceId: e.target.value || undefined}))}
                />
                <input
                    style={s.filterInput}
                    placeholder="Source Reference"
                    value={draft.sourceReferenceId ?? ''}
                    onChange={e => setDraft(d => ({...d, sourceReferenceId: e.target.value || undefined}))}
                />
                <select
                    style={s.filterSelect}
                    value={draft.sourceName ?? ''}
                    onChange={e => setDraft(d => ({...d, sourceName: e.target.value || undefined}))}
                >
                    <option value="">Source Name</option>
                    <option value="IMX">IMX</option>
                    <option value="RMS">RMS</option>
                    <option value="STABLECOIN">STABLECOIN</option>
                </select>
                <select
                    style={s.filterSelect}
                    value={draft.status ?? ''}
                    onChange={e => setDraft(d => ({...d, status: e.target.value as PostingStatus || undefined}))}
                >
                    <option value="">Posting Status</option>
                    <option value="PNDG">PNDG</option>
                    <option value="ACSP">ACSP</option>
                    <option value="RCVD">RCVD</option>
                    <option value="RJCT">RJCT</option>
                </select>
                <select
                    style={s.filterSelect}
                    value={draft.requestType ?? ''}
                    onChange={e => setDraft(d => ({...d, requestType: e.target.value || undefined}))}
                >
                    <option value="">Request Type</option>
                    <option value="IMX_OBPM">IMX_OBPM</option>
                    <option value="IMX_CBS_GL">IMX_CBS_GL</option>
                    <option value="FED_RETURN">FED_RETURN</option>
                    <option value="GL_RETURN">GL_RETURN</option>
                    <option value="MCA_RETURN">MCA_RETURN</option>
                    <option value="BUY_CUSTOMER_POSTING">BUY_CUSTOMER_POSTING</option>
                    <option value="ADD_ACCOUNT_HOLD">ADD_ACCOUNT_HOLD</option>
                    <option value="CUSTOMER_POSTING">CUSTOMER_POSTING</option>
                </select>
                <div style={s.dateRange}>
                    <input
                        type="date"
                        style={s.filterInput}
                        value={draft.fromDate ?? ''}
                        onChange={e => setDraft(d => ({...d, fromDate: e.target.value || undefined}))}
                    />
                    <span style={s.dateSep}>–</span>
                    <input
                        type="date"
                        style={s.filterInput}
                        value={draft.toDate ?? ''}
                        onChange={e => setDraft(d => ({...d, toDate: e.target.value || undefined}))}
                    />
                </div>
                <button type="submit" style={s.searchIconBtn} title="Search">🔍</button>
            </form>

            {/* Status messages */}
            {isLoading && <div style={s.statusMsg}>Loading...</div>}
            {isError && <div style={{...s.statusMsg, color: '#c0392b'}}>Failed to load postings.</div>}

            {/* Table */}
            {data && (
                <>
                    <div style={s.resultCount}>{items.length} result{items.length !== 1 ? 's' : ''}</div>
                    <table style={s.table}>
                        <thead>
                        <tr style={s.theadRow}>
                            <th style={{...s.th, width: 32}}>
                                <input
                                    type="checkbox"
                                    checked={items.length > 0 && items.every(p => selected.has(p.postingId))}
                                    onChange={toggleAll}
                                />
                            </th>
                            <th style={s.th}>Reference Number</th>
                            <th style={s.th}>Source Reference</th>
                            <th style={s.th}>Source Name</th>
                            <th style={s.th}>Request Type</th>
                            <th style={s.th}>Target Systems</th>
                            <th style={s.th}>Exec. Date</th>
                            <th style={s.th}>Amount</th>
                            <th style={s.th}>Currency</th>
                            <th style={s.th}>Payment Status</th>
                            <th style={s.th}>Reason</th>
                            <th style={{...s.th, width: 48}}></th>
                        </tr>
                        </thead>
                        <tbody>
                        {items.map(p => {
                            const expanded = expandedIds.has(p.postingId);
                            return (
                                <Fragment key={p.postingId}>
                                    <tr
                                        onClick={() => navigate(`/postings/${p.postingId}`)}
                                        style={{...s.tbodyRow, background: expanded ? '#eef2ff' : 'white'}}
                                        onMouseEnter={e => (e.currentTarget.style.background = '#f5f8ff')}
                                        onMouseLeave={e => (e.currentTarget.style.background = expanded ? '#eef2ff' : 'white')}
                                    >
                                        <td style={{...s.td, width: 32}} onClick={e => e.stopPropagation()}>
                                            <input
                                                type="checkbox"
                                                checked={selected.has(p.postingId)}
                                                onChange={() => toggleRow(p.postingId)}
                                            />
                                        </td>
                                        <td style={{...s.td, ...s.linkCell}}>{p.endToEndReferenceId}</td>
                                        <td style={s.td}>{p.sourceReferenceId}</td>
                                        <td style={s.td}>{p.sourceName}</td>
                                        <td style={s.td}>{p.requestType}</td>
                                        <td style={s.td}>{p.targetSystems ?? '—'}</td>
                                        <td style={s.td}>{p.requestedExecutionDate}</td>
                                        <td style={s.td}>{p.amount}</td>
                                        <td style={s.td}>{p.currency}</td>
                                        <td style={s.td}><StatusBadge status={p.postingStatus}/></td>
                                        <td style={{...s.td, ...s.reasonCell}} title={p.reason ?? ''}>{p.reason ?? '—'}</td>
                                        <td style={{...s.td, width: 48}} onClick={e => e.stopPropagation()}>
                                            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'flex-end'}}>
                                                <button
                                                    style={{...s.expandBtn, ...(expanded ? s.expandBtnActive : {})}}
                                                    onClick={e => toggleExpand(p.postingId, e)}
                                                    title={expanded ? 'Collapse legs' : 'Expand legs'}
                                                >
                                                    {expanded ? '▲' : '▼'}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                    {expanded && (
                                        <tr style={{background: '#f4f7ff'}}>
                                            <td colSpan={12} style={s.expandedCell}>
                                                <div style={s.expandedLabel}>Posting Legs</div>
                                                <LegTable legs={p.legs ?? []}/>
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            );
                        })}
                        </tbody>
                    </table>
                </>
            )}
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    page: {fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif', color: '#222'},
    header: {display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16},
    title: {margin: 0, fontSize: 22, fontWeight: 600, color: '#1a2a3a'},
    headerActions: {display: 'flex', gap: 8},
    outlineBtn: {
        padding: '7px 14px', border: '1px solid #003b5c', borderRadius: 4,
        background: 'white', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#003b5c',
    },
    solidBtn: {
        padding: '7px 14px', border: 'none', borderRadius: 4,
        background: '#003b5c', color: 'white', cursor: 'pointer', fontSize: 13, fontWeight: 500,
    },
    disabledBtn: {opacity: 0.4, cursor: 'not-allowed'},
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
    dateRange: {display: 'flex', alignItems: 'center', gap: 4},
    dateSep: {color: '#888', fontSize: 14},
    searchIconBtn: {
        padding: '5px 12px', background: '#003b5c', color: 'white', border: 'none',
        borderRadius: 4, cursor: 'pointer', fontSize: 15, height: 30,
    },
    statusMsg: {padding: 24, textAlign: 'center', color: '#666'},
    resultCount: {fontSize: 12, color: '#888', marginBottom: 6},
    table: {
        width: '100%', borderCollapse: 'collapse', fontSize: 13,
        border: '1px solid #dde2ea', borderRadius: 4, overflow: 'hidden',
    },
    theadRow: {background: '#eef1f5', borderBottom: '2px solid #c5cdd8'},
    th: {
        padding: '10px 12px', textAlign: 'left', fontWeight: 600,
        color: '#444', whiteSpace: 'nowrap', borderBottom: '1px solid #dde2ea',
    },
    tbodyRow: {background: 'white', cursor: 'pointer', transition: 'background 0.1s'},
    td: {padding: '10px 12px', borderBottom: '1px solid #eef1f5', verticalAlign: 'middle'},
    linkCell: {color: '#0072ce', fontWeight: 500},
    expandBtn: {
        padding: '3px 8px', border: '1px solid #c5cdd8', borderRadius: 4,
        background: 'white', color: '#555', cursor: 'pointer', fontSize: 10, lineHeight: 1,
    },
    expandBtnActive: {background: '#e8eeff', borderColor: '#7b9ef0', color: '#1a3fa8'},
    expandedCell: {padding: '12px 20px 16px', borderBottom: '2px solid #c5cdd8'},
    expandedLabel: {
        fontSize: 11, fontWeight: 600, color: '#666',
        textTransform: 'uppercase' as const, letterSpacing: 0.5, marginBottom: 8,
    },
    reasonCell: {
        maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis',
        whiteSpace: 'nowrap' as const, color: '#555', fontSize: 12,
    },
};
