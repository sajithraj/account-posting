import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { postingApi } from '../api/postingApi';
const EMPTY_FORM = {
    sourceName: '',
    requestType: '',
    targetSystem: '',
    operation: '',
    orderSeq: 1,
};
export default function ConfigPage() {
    const queryClient = useQueryClient();
    const [showForm, setShowForm] = useState(false);
    const [editing, setEditing] = useState(null);
    const [form, setForm] = useState(EMPTY_FORM);
    const [deleteConfirm, setDeleteConfirm] = useState(null);
    const { data, isLoading, isError } = useQuery({
        queryKey: ['configs'],
        queryFn: () => postingApi.getAllConfigs(),
    });
    const createMutation = useMutation({
        mutationFn: (req) => postingApi.createConfig(req),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['configs'] });
            closeForm();
        },
        onError: (err) => alert(`Failed to create: ${err.message}`),
    });
    const updateMutation = useMutation({
        mutationFn: ({ id, req }) => postingApi.updateConfig(id, req),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['configs'] });
            closeForm();
        },
        onError: (err) => alert(`Failed to update: ${err.message}`),
    });
    const deleteMutation = useMutation({
        mutationFn: (id) => postingApi.deleteConfig(id),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['configs'] });
            setDeleteConfirm(null);
        },
        onError: (err) => alert(`Failed to delete: ${err.message}`),
    });
    const openAdd = () => {
        setEditing(null);
        setForm(EMPTY_FORM);
        setShowForm(true);
    };
    const openEdit = (cfg) => {
        setEditing(cfg);
        setForm({
            sourceName: cfg.sourceName,
            requestType: cfg.requestType,
            targetSystem: cfg.targetSystem,
            operation: cfg.operation,
            orderSeq: cfg.orderSeq,
        });
        setShowForm(true);
    };
    const closeForm = () => {
        setShowForm(false);
        setEditing(null);
        setForm(EMPTY_FORM);
    };
    const handleSubmit = (e) => {
        e.preventDefault();
        if (editing) {
            updateMutation.mutate({ id: editing.configId, req: form });
        }
        else {
            createMutation.mutate(form);
        }
    };
    const isSaving = createMutation.isPending || updateMutation.isPending;
    return (_jsxs("div", { style: s.page, children: [_jsxs("div", { style: s.header, children: [_jsx("h2", { style: s.title, children: "Posting Configuration" }), _jsx("button", { style: s.addBtn, onClick: openAdd, children: "+ ADD CONFIG" })] }), isLoading && _jsx("div", { style: s.statusMsg, children: "Loading..." }), isError && _jsx("div", { style: { ...s.statusMsg, color: '#c0392b' }, children: "Failed to load configs." }), data && (_jsxs("table", { style: s.table, children: [_jsx("thead", { children: _jsxs("tr", { style: s.theadRow, children: [_jsx("th", { style: s.th, children: "Config ID" }), _jsx("th", { style: s.th, children: "Source Name" }), _jsx("th", { style: s.th, children: "Request Type" }), _jsx("th", { style: s.th, children: "Target System" }), _jsx("th", { style: s.th, children: "Operation" }), _jsx("th", { style: s.th, children: "Order" }), _jsx("th", { style: { ...s.th, width: 110 }, children: "Actions" })] }) }), _jsx("tbody", { children: data.map(cfg => (_jsxs("tr", { style: s.tbodyRow, children: [_jsx("td", { style: s.td, children: cfg.configId }), _jsx("td", { style: s.td, children: cfg.sourceName }), _jsx("td", { style: s.td, children: cfg.requestType }), _jsx("td", { style: s.td, children: cfg.targetSystem }), _jsx("td", { style: s.td, children: cfg.operation }), _jsx("td", { style: s.td, children: cfg.orderSeq }), _jsxs("td", { style: s.td, children: [_jsx("button", { style: s.editBtn, onClick: () => openEdit(cfg), children: "Edit" }), deleteConfirm === cfg.configId ? (_jsxs(_Fragment, { children: [_jsx("button", { style: s.confirmBtn, onClick: () => deleteMutation.mutate(cfg.configId), disabled: deleteMutation.isPending, children: "Confirm" }), _jsx("button", { style: s.cancelBtn, onClick: () => setDeleteConfirm(null), children: "\u2715" })] })) : (_jsx("button", { style: s.deleteBtn, onClick: () => setDeleteConfirm(cfg.configId), children: "Delete" }))] })] }, cfg.configId))) })] })), showForm && (_jsx("div", { style: s.overlay, children: _jsxs("div", { style: s.modal, children: [_jsx("h3", { style: s.modalTitle, children: editing ? 'Edit Config' : 'Add Config' }), _jsxs("form", { onSubmit: handleSubmit, children: [[
                                    ['sourceName', 'Source Name'],
                                    ['requestType', 'Request Type'],
                                    ['targetSystem', 'Target System'],
                                    ['operation', 'Operation'],
                                ].map(([field, label]) => (_jsxs("div", { style: s.formRow, children: [_jsxs("label", { style: s.label, children: [label, " *"] }), _jsx("input", { style: s.input, value: form[field], onChange: e => setForm(f => ({ ...f, [field]: e.target.value })), required: true })] }, field))), _jsxs("div", { style: s.formRow, children: [_jsx("label", { style: s.label, children: "Order Seq *" }), _jsx("input", { type: "number", min: 1, style: s.input, value: form.orderSeq, onChange: e => setForm(f => ({ ...f, orderSeq: Number(e.target.value) })), required: true })] }), _jsxs("div", { style: s.modalActions, children: [_jsx("button", { type: "button", style: s.cancelModalBtn, onClick: closeForm, children: "Cancel" }), _jsx("button", { type: "submit", style: { ...s.saveBtn, ...(isSaving ? s.disabled : {}) }, disabled: isSaving, children: isSaving ? 'Saving...' : editing ? 'Update' : 'Create' })] })] })] }) }))] }));
}
const s = {
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
    title: { margin: 0, fontSize: 22, fontWeight: 600, color: '#1a2a3a' },
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
    statusMsg: { padding: 24, textAlign: 'center', color: '#666' },
    table: {
        width: '100%',
        borderCollapse: 'collapse',
        fontSize: 13,
        border: '1px solid #dde2ea',
    },
    theadRow: { background: '#eef1f5', borderBottom: '2px solid #c5cdd8' },
    th: { padding: '10px 12px', textAlign: 'left', fontWeight: 600, color: '#444', whiteSpace: 'nowrap' },
    tbodyRow: { background: 'white', borderBottom: '1px solid #eef1f5' },
    td: { padding: '9px 12px', verticalAlign: 'middle' },
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
    modalTitle: { margin: '0 0 20px', fontSize: 17, fontWeight: 600, color: '#1a2a3a' },
    formRow: { marginBottom: 14 },
    label: { display: 'block', fontSize: 12, fontWeight: 600, color: '#555', marginBottom: 4 },
    input: {
        width: '100%', padding: '6px 10px', fontSize: 13,
        border: '1px solid #c5cdd8', borderRadius: 4, boxSizing: 'border-box',
    },
    modalActions: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 20 },
    cancelModalBtn: {
        padding: '7px 16px', fontSize: 13, cursor: 'pointer',
        border: '1px solid #b0bec5', borderRadius: 4, background: 'white', color: '#333',
    },
    saveBtn: {
        padding: '7px 20px', fontSize: 13, cursor: 'pointer',
        border: 'none', borderRadius: 4, background: '#003b5c', color: 'white', fontWeight: 500,
    },
    disabled: { opacity: 0.6, cursor: 'not-allowed' },
};
