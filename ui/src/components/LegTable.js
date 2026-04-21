import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
export default function LegTable({ legs, onUpdateStatus, updatingLegOrder }) {
    const [popup, setPopup] = useState(null);
    if (legs.length === 0) {
        return _jsx("p", { style: { color: '#888' }, children: "No legs found for this posting." });
    }
    const showActions = !!onUpdateStatus;
    const openPopup = (leg) => {
        setPopup({
            transactionOrder: leg.transactionOrder,
            currentStatus: leg.status,
            selectedStatus: leg.status === 'SUCCESS' ? 'FAILED' : 'SUCCESS',
            reason: '',
        });
    };
    const handleApply = () => {
        if (!popup)
            return;
        onUpdateStatus(popup.transactionOrder, popup.selectedStatus, popup.reason || undefined);
        setPopup(null);
    };
    return (_jsxs(_Fragment, { children: [popup && (_jsx("div", { style: overlay, children: _jsxs("div", { style: dialog, children: [_jsx("h3", { style: { margin: '0 0 16px', fontSize: 16, fontWeight: 600 }, children: "Manual Leg Update" }), _jsx("label", { style: labelStyle, children: "Status" }), _jsxs("select", { style: { ...inputStyle, marginBottom: 14 }, value: popup.selectedStatus, onChange: e => setPopup(p => p && { ...p, selectedStatus: e.target.value }), children: [_jsx("option", { value: "SUCCESS", children: "SUCCESS" }), _jsx("option", { value: "FAILED", children: "FAILED" })] }), _jsxs("label", { style: labelStyle, children: ["Reason ", _jsx("span", { style: { color: '#888', fontWeight: 400 }, children: "(optional)" })] }), _jsx("textarea", { style: { ...inputStyle, height: 72, resize: 'vertical' }, placeholder: "e.g. CBS manually confirmed \u2014 reference CBS-20260325", value: popup.reason, onChange: e => setPopup(p => p && { ...p, reason: e.target.value }) }), _jsxs("div", { style: { display: 'flex', gap: 8, marginTop: 18, justifyContent: 'flex-end' }, children: [_jsx("button", { style: cancelBtn, onClick: () => setPopup(null), children: "Cancel" }), _jsx("button", { style: {
                                        ...applyBtn,
                                        ...(updatingLegOrder === popup.transactionOrder ? disabledStyle : {}),
                                    }, disabled: updatingLegOrder === popup.transactionOrder || popup.selectedStatus === popup.currentStatus, onClick: handleApply, children: updatingLegOrder === popup.transactionOrder ? 'Saving…' : 'Apply' })] })] }) })), _jsxs("table", { style: { width: '100%', borderCollapse: 'collapse', fontSize: 13 }, children: [_jsx("thead", { children: _jsxs("tr", { style: { background: '#eef1f5' }, children: [_jsx("th", { style: th, children: "Order" }), _jsx("th", { style: th, children: "Target System" }), _jsx("th", { style: th, children: "Operation" }), _jsx("th", { style: th, children: "Account" }), _jsx("th", { style: th, children: "Reference ID" }), _jsx("th", { style: th, children: "Status" }), _jsx("th", { style: th, children: "Mode" }), _jsx("th", { style: th, children: "Posted Time" }), _jsx("th", { style: th, children: "Reason" }), showActions && _jsx("th", { style: th, children: "Manual Update" })] }) }), _jsx("tbody", { children: legs.map((leg, i) => {
                            const isSuccess = leg.status === 'SUCCESS';
                            const isUpdating = updatingLegOrder === leg.transactionOrder;
                            return (_jsxs("tr", { children: [_jsx("td", { style: { ...td, textAlign: 'center', fontWeight: 600 }, children: leg.transactionOrder }), _jsx("td", { style: td, children: leg.targetSystem }), _jsx("td", { style: td, children: leg.operation }), _jsx("td", { style: td, children: leg.account }), _jsx("td", { style: td, children: leg.referenceId ?? '—' }), _jsx("td", { style: td, children: _jsx("span", { style: {
                                                fontWeight: 600,
                                                color: leg.status === 'SUCCESS' ? '#0a3622'
                                                    : leg.status === 'FAILED' ? '#58151c'
                                                        : '#856404',
                                            }, children: leg.status }) }), _jsx("td", { style: td, children: _jsx("span", { style: {
                                                fontSize: 11,
                                                fontWeight: 600,
                                                padding: '2px 6px',
                                                borderRadius: 3,
                                                background: leg.mode === 'MANUAL' ? '#fef3c7'
                                                    : leg.mode === 'RETRY' ? '#dbeafe'
                                                        : '#f3f4f6',
                                                color: leg.mode === 'MANUAL' ? '#92400e'
                                                    : leg.mode === 'RETRY' ? '#1e40af'
                                                        : '#374151',
                                            }, children: leg.mode ?? 'NORM' }) }), _jsx("td", { style: td, children: leg.postedTime ? new Date(leg.postedTime).toLocaleString() : '—' }), _jsx("td", { style: td, children: leg.reason ?? '—' }), showActions && (_jsx("td", { style: { ...td, whiteSpace: 'nowrap' }, children: !isSuccess ? (_jsx("button", { style: {
                                                ...updateBtn,
                                                ...(isUpdating ? disabledStyle : {}),
                                            }, disabled: isUpdating, onClick: () => openPopup(leg), children: isUpdating ? '…' : 'Update' })) : (_jsx("span", { style: { color: '#aaa', fontSize: 12 }, children: "\u2014" })) }))] }, i));
                        }) })] })] }));
}
const th = {
    padding: '9px 12px',
    textAlign: 'left',
    fontWeight: 600,
    color: '#444',
    borderBottom: '2px solid #c5cdd8',
    whiteSpace: 'nowrap'
};
const td = { padding: '8px 12px', borderBottom: '1px solid #eef1f5' };
const updateBtn = {
    fontSize: 12,
    padding: '3px 10px',
    border: '1px solid #003b5c',
    borderRadius: 3,
    background: 'white',
    color: '#003b5c',
    cursor: 'pointer'
};
const applyBtn = {
    fontSize: 13,
    padding: '6px 18px',
    border: 'none',
    borderRadius: 4,
    background: '#003b5c',
    color: 'white',
    cursor: 'pointer',
    fontWeight: 500
};
const cancelBtn = {
    fontSize: 13,
    padding: '6px 18px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    background: 'white',
    color: '#444',
    cursor: 'pointer'
};
const disabledStyle = { opacity: 0.5, cursor: 'not-allowed' };
const overlay = {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0,0,0,0.35)',
    zIndex: 1000,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center'
};
const dialog = {
    background: 'white',
    borderRadius: 8,
    padding: '24px 28px',
    width: 380,
    boxShadow: '0 8px 30px rgba(0,0,0,0.18)',
    display: 'flex',
    flexDirection: 'column'
};
const labelStyle = {
    fontSize: 12,
    fontWeight: 600,
    color: '#555',
    marginBottom: 5,
    display: 'block'
};
const inputStyle = {
    width: '100%',
    fontSize: 13,
    padding: '7px 10px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    boxSizing: 'border-box',
    fontFamily: 'inherit',
    display: 'block'
};
