import {Fragment, useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {getErrorMessage, postingApi} from '../api/postingApi';
import type {PostingFilterDraft, PostingSearchRequest, PostingStatus, SearchCondition} from '../types/posting';
import StatusBadge from '../components/StatusBadge';
import LegTable from '../components/LegTable';

export default function PostingListPage() {
    const navigate = useNavigate();
    const queryClient = useQueryClient();

    const [pageSize, setPageSize] = useState(10);
    const [searchRequest, setSearchRequest] = useState<PostingSearchRequest>({pagination: {offset: 1, limit: 10}});
    const [draft, setDraft] = useState<PostingFilterDraft>({});
    const [selected, setSelected] = useState<Set<number>>(new Set());

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
        mutationFn: (ids: number[]) => postingApi.retry(ids),
        onSuccess: () => {
            setSelected(new Set());
            queryClient.invalidateQueries({queryKey: ['postings']});
        },
        onError: (err: unknown) => alert(`Retry failed: ${getErrorMessage(err)}`),
    });

    const buildConditions = (f: PostingFilterDraft): SearchCondition[] => {
        const conds: SearchCondition[] = [];
        if (f.endToEndReferenceId) conds.push({
            property: 'end_to_end_reference_id',
            operator: 'EQUALS',
            values: [f.endToEndReferenceId]
        });
        if (f.sourceReferenceId) conds.push({
            property: 'source_reference_id',
            operator: 'EQUALS',
            values: [f.sourceReferenceId]
        });
        if (f.sourceName) conds.push({property: 'source_name', operator: 'EQUALS', values: [f.sourceName]});
        if (f.targetSystem) conds.push({property: 'target_system', operator: 'CONTAINS', values: [f.targetSystem]});
        if (f.status) conds.push({property: 'status', operator: 'EQUALS', values: [f.status]});
        if (f.requestType) conds.push({property: 'request_type', operator: 'EQUALS', values: [f.requestType]});
        if (f.fromDate && f.toDate) {
            conds.push({property: 'requested_execution_date', operator: 'BETWEEN', values: [f.fromDate, f.toDate]});
        } else if (f.fromDate) {
            conds.push({property: 'requested_execution_date', operator: 'GREATER_THAN', values: [f.fromDate]});
        } else if (f.toDate) {
            conds.push({property: 'requested_execution_date', operator: 'LESS_THAN', values: [f.toDate]});
        }
        return conds;
    };

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        setSearchRequest({conditions: buildConditions(draft), pagination: {offset: 1, limit: pageSize}});
    };

    const totalItems = data?.totalItems ?? 0;
    const currentOffset = data?.offset ?? 1;
    const currentLimit = data?.limit ?? pageSize;
    const totalPages = Math.ceil(totalItems / currentLimit);
    const currentPage = Math.floor((currentOffset - 1) / currentLimit);
    const from = totalItems === 0 ? 0 : currentOffset;
    const to = Math.min(currentOffset + currentLimit - 1, totalItems);

    const pendingIds = (data?.items ?? [])
        .filter(p => p.postingStatus === 'PNDG')
        .map(p => p.postingId);
    const hasPending = pendingIds.length > 0;

    const selectedPendingIds = [...selected].filter(id =>
        (data?.items ?? []).some(p => p.postingId === id && p.postingStatus === 'PNDG'),
    );
    const hasSelectedPending = selectedPendingIds.length > 0;

    const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

    const toggleExpand = (id: number, e: React.MouseEvent) => {
        e.stopPropagation();
        setExpandedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const toggleRow = (id: number) => {
        setSelected(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const toggleAll = () => {
        if (!data) return;
        const allIds = data.items.map(p => p.postingId);
        const allSelected = allIds.every(id => selected.has(id));
        if (allSelected) {
            setSelected(new Set());
        } else {
            setSelected(new Set(allIds));
        }
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
                            setSearchRequest({pagination: {offset: 1, limit: pageSize}});
                        }}
                        disabled={Object.values(draft).every(v => v === undefined || v === '')}
                    >
                        ✕ CLEAR FILTERS
                    </button>
                    <button
                        style={{
                            ...s.outlineBtn,
                            ...(hasSelectedPending ? {} : s.disabledBtn),
                        }}
                        onClick={() => retrySelectedMutation.mutate(selectedPendingIds)}
                        disabled={!hasSelectedPending || retrySelectedMutation.isPending}
                    >
                        {retrySelectedMutation.isPending ? 'Processing...' : '⟳ RETRY SELECTED'}
                    </button>
                    <button
                        style={{
                            ...s.solidBtn,
                            ...(!hasPending || retryAllMutation.isPending ? s.disabledBtn : {}),
                        }}
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
                    value={draft.targetSystem ?? ''}
                    onChange={e => setDraft(d => ({...d, targetSystem: e.target.value || undefined}))}
                >
                    <option value="">Target System</option>
                    <option value="CBS">CBS</option>
                    <option value="GL">GL</option>
                    <option value="OBPM">OBPM</option>
                </select>

                <select
                    style={s.filterSelect}
                    value={draft.status ?? ''}
                    onChange={e => setDraft(d => ({...d, status: e.target.value as PostingStatus || undefined}))}
                >
                    <option value="">Posting Status</option>
                    <option value="PNDG">PNDG</option>
                    <option value="ACSP">ACSP</option>
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
                    <table style={s.table}>
                        <thead>
                        <tr style={s.theadRow}>
                            <th style={{...s.th, width: 32}}>
                                <input
                                    type="checkbox"
                                    checked={data.items.length > 0 && data.items.every(p => selected.has(p.postingId))}
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
                        {data.items.map(p => {
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
                                        <td style={{...s.td, ...s.reasonCell}}
                                            title={p.reason ?? ''}>{p.reason ?? '—'}</td>
                                        <td style={{...s.td, width: 48}} onClick={e => e.stopPropagation()}>
                                            <div style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'flex-end'
                                            }}>
                                                <button
                                                    style={{
                                                        ...s.expandBtn,
                                                        ...(expanded ? s.expandBtnActive : {}),
                                                    }}
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
                                                <LegTable legs={p.responses ?? []}/>
                                            </td>
                                        </tr>
                                    )}
                                </Fragment>
                            );
                        })}
                        </tbody>
                    </table>

                    {/* Pagination */}
                    <div style={s.pagination}>
                        <span style={s.paginationLabel}>Rows per page:</span>
                        <select
                            style={s.pageSizeSelect}
                            value={pageSize}
                            onChange={e => {
                                const sz = Number(e.target.value);
                                setPageSize(sz);
                                setSearchRequest(r => ({...r, pagination: {offset: 1, limit: sz}}));
                            }}
                        >
                            {[10, 20, 50].map(sz => <option key={sz} value={sz}>{sz}</option>)}
                        </select>
                        <span style={s.paginationLabel}>{from}–{to} of {totalItems}</span>
                        <button
                            style={s.pageBtn}
                            disabled={currentPage === 0}
                            onClick={() => setSearchRequest(r => ({
                                ...r,
                                pagination: {offset: Math.max(1, currentOffset - currentLimit), limit: currentLimit},
                            }))}
                        >‹
                        </button>
                        <button
                            style={s.pageBtn}
                            disabled={currentPage + 1 >= totalPages}
                            onClick={() => setSearchRequest(r => ({
                                ...r,
                                pagination: {offset: currentOffset + currentLimit, limit: currentLimit},
                            }))}
                        >›
                        </button>
                    </div>
                </>
            )}
        </div>
    );
}

const s: Record<string, React.CSSProperties> = {
    page: {
        fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        color: '#222',
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 16,
    },
    title: {
        margin: 0,
        fontSize: 22,
        fontWeight: 600,
        color: '#1a2a3a',
    },
    headerActions: {
        display: 'flex',
        gap: 8,
    },
    outlineBtn: {
        padding: '7px 14px',
        border: '1px solid #003b5c',
        borderRadius: 4,
        background: 'white',
        cursor: 'pointer',
        fontSize: 13,
        fontWeight: 500,
        color: '#003b5c',
    },
    solidBtn: {
        padding: '7px 14px',
        border: 'none',
        borderRadius: 4,
        background: '#003b5c',
        color: 'white',
        cursor: 'pointer',
        fontSize: 13,
        fontWeight: 500,
    },
    disabledBtn: {
        opacity: 0.4,
        cursor: 'not-allowed',
    },
    filterBar: {
        display: 'flex',
        gap: 8,
        flexWrap: 'wrap',
        alignItems: 'center',
        padding: '12px 16px',
        background: '#f4f6f9',
        border: '1px solid #dde2ea',
        borderRadius: 6,
        marginBottom: 16,
    },
    filterInput: {
        padding: '5px 10px',
        border: '1px solid #c5cdd8',
        borderRadius: 4,
        fontSize: 13,
        height: 30,
        outline: 'none',
        background: 'white',
    },
    filterSelect: {
        padding: '5px 8px',
        border: '1px solid #c5cdd8',
        borderRadius: 4,
        fontSize: 13,
        height: 30,
        background: 'white',
        cursor: 'pointer',
    },
    dateRange: {
        display: 'flex',
        alignItems: 'center',
        gap: 4,
    },
    dateSep: {
        color: '#888',
        fontSize: 14,
    },
    searchIconBtn: {
        padding: '5px 12px',
        background: '#003b5c',
        color: 'white',
        border: 'none',
        borderRadius: 4,
        cursor: 'pointer',
        fontSize: 15,
        height: 30,
    },
    statusMsg: {
        padding: 24,
        textAlign: 'center',
        color: '#666',
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: 13,
        border: '1px solid #dde2ea',
        borderRadius: 4,
        overflow: 'hidden',
    },
    theadRow: {
        background: '#eef1f5',
        borderBottom: '2px solid #c5cdd8',
    },
    th: {
        padding: '10px 12px',
        textAlign: 'left',
        fontWeight: 600,
        color: '#444',
        whiteSpace: 'nowrap',
        borderBottom: '1px solid #dde2ea',
    },
    tbodyRow: {
        background: 'white',
        cursor: 'pointer',
        transition: 'background 0.1s',
    },
    td: {
        padding: '10px 12px',
        borderBottom: '1px solid #eef1f5',
        verticalAlign: 'middle',
    },
    linkCell: {
        color: '#0072ce',
        fontWeight: 500,
    },
    pagination: {
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '12px 4px',
        justifyContent: 'flex-end',
        fontSize: 13,
        color: '#555',
    },
    paginationLabel: {
        color: '#666',
    },
    pageSizeSelect: {
        border: '1px solid #c5cdd8',
        borderRadius: 4,
        padding: '2px 6px',
        fontSize: 13,
    },
    pageBtn: {
        padding: '4px 10px',
        border: '1px solid #c5cdd8',
        borderRadius: 4,
        background: 'white',
        cursor: 'pointer',
        fontSize: 16,
        lineHeight: 1,
    },
    expandBtn: {
        padding: '3px 8px',
        border: '1px solid #c5cdd8',
        borderRadius: 4,
        background: 'white',
        color: '#555',
        cursor: 'pointer',
        fontSize: 10,
        lineHeight: 1,
    },
    expandBtnActive: {
        background: '#e8eeff',
        borderColor: '#7b9ef0',
        color: '#1a3fa8',
    },
    expandedCell: {
        padding: '12px 20px 16px',
        borderBottom: '2px solid #c5cdd8',
    },
    reasonCell: {
        maxWidth: 220,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap' as const,
        color: '#555',
        fontSize: 12,
    },
    expandedLabel: {
        fontSize: 11,
        fontWeight: 600,
        color: '#666',
        textTransform: 'uppercase' as const,
        letterSpacing: 0.5,
        marginBottom: 8,
    },
};
