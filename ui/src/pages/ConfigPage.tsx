import {useState} from 'react';
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query';
import {getErrorMessage, postingApi} from '../api/postingApi';
import type {PostingConfigRequest, PostingConfigResponse} from '../types/posting';

const EMPTY_FORM: PostingConfigRequest = {
    sourceName: '',
    requestType: '',
    targetSystem: '',
    operation: '',
    orderSeq: 1,
    processingMode: 'ASYNC',
};

export default function ConfigPage() {
    const queryClient = useQueryClient();

    const [showForm, setShowForm] = useState(false);
    const [editing, setEditing] = useState<PostingConfigResponse | null>(null);
    const [form, setForm] = useState<PostingConfigRequest>(EMPTY_FORM);
    const [deleteConfirm, setDeleteConfirm] = useState<{ requestType: string; orderSeq: number } | null>(null);

    const {data, isLoading, isError} = useQuery({
        queryKey: ['configs'],
        queryFn: () => postingApi.getAllConfigs(),
    });

    const createMutation = useMutation({
        mutationFn: (req: PostingConfigRequest) => postingApi.createConfig(req),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['configs']});
            closeForm();
        },
        onError: err => alert(`Failed to create: ${getErrorMessage(err)}`),
    });

    const updateMutation = useMutation({
        mutationFn: ({requestType, orderSeq, req}: { requestType: string; orderSeq: number; req: PostingConfigRequest }) =>
            postingApi.updateConfig(requestType, orderSeq, req),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['configs']});
            closeForm();
        },
        onError: err => alert(`Failed to update: ${getErrorMessage(err)}`),
    });

    const deleteMutation = useMutation({
        mutationFn: ({requestType, orderSeq}: { requestType: string; orderSeq: number }) =>
            postingApi.deleteConfig(requestType, orderSeq),
        onSuccess: () => {
            queryClient.invalidateQueries({queryKey: ['configs']});
            setDeleteConfirm(null);
        },
        onError: err => alert(`Failed to delete: ${getErrorMessage(err)}`),
    });

    const openAdd = () => {
        setEditing(null);
        setForm(EMPTY_FORM);
        setShowForm(true);
    };

    const openEdit = (cfg: PostingConfigResponse) => {
        setEditing(cfg);
        setForm({
            sourceName: cfg.sourceName,
            requestType: cfg.requestType,
            targetSystem: cfg.targetSystem,
            operation: cfg.operation,
            orderSeq: cfg.orderSeq,
            processingMode: cfg.processingMode,
        });
        setShowForm(true);
    };

    const closeForm = () => {
        setShowForm(false);
        setEditing(null);
        setForm(EMPTY_FORM);
    };

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (editing) {
            updateMutation.mutate({requestType: editing.requestType, orderSeq: editing.orderSeq, req: form});
        } else {
            createMutation.mutate(form);
        }
    };

    const isSaving = createMutation.isPending || updateMutation.isPending;

    return (
        <div style={s.page}>
            {/* Header */}
            <div style={s.header}>
                <h2 style={s.title}>Posting Configuration</h2>
                <button style={s.addBtn} onClick={openAdd}>+ ADD CONFIG</button>
            </div>

            {/* Status */}
            {isLoading && <div style={s.statusMsg}>Loading...</div>}
            {isError && <div style={{...s.statusMsg, color: '#c0392b'}}>Failed to load configs.</div>}

            {/* Table */}
            {data && (
                <table style={s.table}>
                    <thead>
                    <tr style={s.theadRow}>
                        <th style={s.th}>Request Type</th>
                        <th style={s.th}>Order</th>
                        <th style={s.th}>Source Name</th>
                        <th style={s.th}>Target System</th>
                        <th style={s.th}>Operation</th>
                        <th style={s.th}>Processing Mode</th>
                        <th style={{...s.th, width: 110}}>Actions</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.map(cfg => {
                        const key = `${cfg.requestType}-${cfg.orderSeq}`;
                        const isConfirming = deleteConfirm?.requestType === cfg.requestType && deleteConfirm?.orderSeq === cfg.orderSeq;
                        return (
                            <tr key={key} style={s.tbodyRow}>
                                <td style={s.td}>{cfg.requestType}</td>
                                <td style={s.td}>{cfg.orderSeq}</td>
                                <td style={s.td}>{cfg.sourceName}</td>
                                <td style={s.td}>{cfg.targetSystem}</td>
                                <td style={s.td}>{cfg.operation}</td>
                                <td style={s.td}>{cfg.processingMode}</td>
                                <td style={s.td}>
                                    <button style={s.editBtn} onClick={() => openEdit(cfg)}>Edit</button>
                                    {isConfirming ? (
                                        <>
                                            <button
                                                style={s.confirmBtn}
                                                onClick={() => deleteMutation.mutate({requestType: cfg.requestType, orderSeq: cfg.orderSeq})}
                                                disabled={deleteMutation.isPending}
                                            >
                                                Confirm
                                            </button>
                                            <button style={s.cancelBtn} onClick={() => setDeleteConfirm(null)}>✕</button>
                                        </>
                                    ) : (
                                        <button style={s.deleteBtn}
                                                onClick={() => setDeleteConfirm({requestType: cfg.requestType, orderSeq: cfg.orderSeq})}>Delete</button>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            )}

            {/* Add / Edit modal */}
            {showForm && (
                <div style={s.overlay}>
                    <div style={s.modal}>
                        <h3 style={s.modalTitle}>{editing ? 'Edit Config' : 'Add Config'}</h3>
                        <form onSubmit={handleSubmit}>
                            {(
                                [
                                    ['sourceName', 'Source Name'],
                                    ['requestType', 'Request Type'],
                                    ['targetSystem', 'Target System'],
                                    ['operation', 'Operation'],
                                    ['processingMode', 'Processing Mode (SYNC / ASYNC)'],
                                ] as [keyof PostingConfigRequest, string][]
                            ).map(([field, label]) => (
                                <div key={field} style={s.formRow}>
                                    <label style={s.label}>{label} *</label>
                                    <input
                                        style={s.input}
                                        value={form[field] as string}
                                        onChange={e => setForm(f => ({...f, [field]: e.target.value}))}
                                        required
                                    />
                                </div>
                            ))}
                            <div style={s.formRow}>
                                <label style={s.label}>Order Seq *</label>
                                <input
                                    type="number"
                                    min={1}
                                    style={s.input}
                                    value={form.orderSeq}
                                    onChange={e => setForm(f => ({...f, orderSeq: Number(e.target.value)}))}
                                    required
                                />
                            </div>
                            <div style={s.modalActions}>
                                <button type="button" style={s.cancelModalBtn} onClick={closeForm}>Cancel</button>
                                <button type="submit" style={{...s.saveBtn, ...(isSaving ? s.disabled : {})}}
                                        disabled={isSaving}>
                                    {isSaving ? 'Saving...' : editing ? 'Update' : 'Create'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
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
    title: {margin: 0, fontSize: 22, fontWeight: 600, color: '#1a2a3a'},
    addBtn: {
        padding: '7px 16px',
        background: '#003b5c',
        color: 'white',
        border: 'none',
        borderRadius: 4,
        cursor: 'pointer',
        fontSize: 13,
        fontWeight: 500,
    },
    statusMsg: {padding: 24, textAlign: 'center', color: '#666'},
    table: {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: 13,
        border: '1px solid #dde2ea',
    },
    theadRow: {background: '#eef1f5', borderBottom: '2px solid #c5cdd8'},
    th: {padding: '10px 12px', textAlign: 'left', fontWeight: 600, color: '#444', whiteSpace: 'nowrap'},
    tbodyRow: {background: 'white', borderBottom: '1px solid #eef1f5'},
    td: {padding: '9px 12px', verticalAlign: 'middle'},
    editBtn: {
        padding: '3px 10px', marginRight: 4, fontSize: 12, cursor: 'pointer',
        border: '1px solid #0072ce', borderRadius: 3, background: 'white', color: '#0072ce',
    },
    deleteBtn: {
        padding: '3px 10px', fontSize: 12, cursor: 'pointer',
        border: '1px solid #c0392b', borderRadius: 3, background: 'white', color: '#c0392b',
    },
    confirmBtn: {
        padding: '3px 10px', marginRight: 4, fontSize: 12, cursor: 'pointer',
        border: 'none', borderRadius: 3, background: '#c0392b', color: 'white',
    },
    cancelBtn: {
        padding: '3px 8px', fontSize: 12, cursor: 'pointer',
        border: '1px solid #aaa', borderRadius: 3, background: 'white', color: '#555',
    },
    overlay: {
        position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
    },
    modal: {
        background: 'white', borderRadius: 8, padding: 28, width: 420,
        boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
    },
    modalTitle: {margin: '0 0 20px', fontSize: 17, fontWeight: 600, color: '#1a2a3a'},
    formRow: {marginBottom: 14},
    label: {display: 'block', fontSize: 12, fontWeight: 600, color: '#555', marginBottom: 4},
    input: {
        width: '100%', padding: '6px 10px', fontSize: 13,
        border: '1px solid #c5cdd8', borderRadius: 4, boxSizing: 'border-box' as const,
    },
    modalActions: {display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20},
    cancelModalBtn: {
        padding: '7px 16px', fontSize: 13, cursor: 'pointer',
        border: '1px solid #b0bec5', borderRadius: 4, background: 'white', color: '#333',
    },
    saveBtn: {
        padding: '7px 20px', fontSize: 13, cursor: 'pointer',
        border: 'none', borderRadius: 4, background: '#003b5c', color: 'white', fontWeight: 500,
    },
    disabled: {opacity: 0.6, cursor: 'not-allowed'},
};
