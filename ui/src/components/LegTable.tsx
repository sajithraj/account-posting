import {useState} from 'react';
import type {LegResponse} from '../types/posting';

interface Props {
    legs: LegResponse[];
    /** When provided, a "Manual Update" action column is shown for non-SUCCESS legs. */
    onUpdateStatus?: (transactionId: number, status: string, reason?: string) => void;
    updatingLegId?: number | null;
}

interface PopupState {
    legId: number;
    currentStatus: string;
    selectedStatus: string;
    reason: string;
}

export default function LegTable({legs, onUpdateStatus, updatingLegId}: Props) {
    const [popup, setPopup] = useState<PopupState | null>(null);

    if (legs.length === 0) {
        return <p style={{color: '#888'}}>No legs found for this posting.</p>;
    }

    const showActions = !!onUpdateStatus;

    const openPopup = (leg: LegResponse) => {
        if (!leg.transactionId) return;
        setPopup({
            legId: leg.transactionId,
            currentStatus: leg.status,
            selectedStatus: leg.status === 'SUCCESS' ? 'FAILED' : 'SUCCESS',
            reason: '',
        });
    };

    const handleApply = () => {
        if (!popup) return;
        onUpdateStatus!(popup.legId, popup.selectedStatus, popup.reason || undefined);
        setPopup(null);
    };

    return (
        <>
            {/* ── Popup dialog ─────────────────────────────────────────── */}
            {popup && (
                <div style={overlay}>
                    <div style={dialog}>
                        <h3 style={{margin: '0 0 16px', fontSize: 16, fontWeight: 600}}>
                            Manual Leg Update
                        </h3>

                        <label style={labelStyle}>Status</label>
                        <select
                            style={{...inputStyle, marginBottom: 14}}
                            value={popup.selectedStatus}
                            onChange={e => setPopup(p => p && {...p, selectedStatus: e.target.value})}
                        >
                            <option value="SUCCESS">SUCCESS</option>
                            <option value="FAILED">FAILED</option>
                        </select>

                        <label style={labelStyle}>Reason <span
                            style={{color: '#888', fontWeight: 400}}>(optional)</span></label>
                        <textarea
                            style={{...inputStyle, height: 72, resize: 'vertical'}}
                            placeholder="e.g. CBS manually confirmed — reference CBS-20260325"
                            value={popup.reason}
                            onChange={e => setPopup(p => p && {...p, reason: e.target.value})}
                        />

                        <div style={{display: 'flex', gap: 8, marginTop: 18, justifyContent: 'flex-end'}}>
                            <button style={cancelBtn} onClick={() => setPopup(null)}>Cancel</button>
                            <button
                                style={{
                                    ...applyBtn,
                                    ...(updatingLegId === popup.legId ? disabledStyle : {}),
                                }}
                                disabled={updatingLegId === popup.legId || popup.selectedStatus === popup.currentStatus}
                                onClick={handleApply}
                            >
                                {updatingLegId === popup.legId ? 'Saving…' : 'Apply'}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ── Leg table ────────────────────────────────────────────── */}
            <table style={{width: '100%', borderCollapse: 'collapse', fontSize: 13}}>
                <thead>
                <tr style={{background: '#eef1f5'}}>
                    <th style={th}>Order</th>
                    <th style={th}>Target System</th>
                    <th style={th}>Operation</th>
                    <th style={th}>Account</th>
                    <th style={th}>Reference ID</th>
                    <th style={th}>Status</th>
                    <th style={th}>Mode</th>
                    <th style={th}>Posted Time</th>
                    <th style={th}>Reason</th>
                    {showActions && <th style={th}>Manual Update</th>}
                </tr>
                </thead>
                <tbody>
                {legs.map((leg, i) => {
                    const isSuccess = leg.status === 'SUCCESS';
                    const isUpdating = updatingLegId === leg.transactionId;

                    return (
                        <tr key={i}>
                            <td style={{...td, textAlign: 'center', fontWeight: 600}}>{leg.transactionOrder}</td>
                            <td style={td}>{leg.targetSystem}</td>
                            <td style={td}>{leg.operation}</td>
                            <td style={td}>{leg.account}</td>
                            <td style={td}>{leg.referenceId ?? '—'}</td>
                            <td style={td}>
                  <span style={{
                      fontWeight: 600,
                      color: leg.status === 'SUCCESS' ? '#0a3622'
                          : leg.status === 'FAILED' ? '#58151c'
                              : '#856404',
                  }}>
                    {leg.status}
                  </span>
                            </td>
                            <td style={td}>
                  <span style={{
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
                  }}>
                    {leg.mode ?? 'NORM'}
                  </span>
                            </td>
                            <td style={td}>{leg.postedTime ? new Date(leg.postedTime).toLocaleString() : '—'}</td>
                            <td style={td}>{leg.reason ?? '—'}</td>
                            {showActions && (
                                <td style={{...td, whiteSpace: 'nowrap'}}>
                                    {!isSuccess ? (
                                        <button
                                            style={{
                                                ...updateBtn,
                                                ...(isUpdating ? disabledStyle : {}),
                                            }}
                                            disabled={isUpdating || !leg.transactionId}
                                            onClick={() => openPopup(leg)}
                                        >
                                            {isUpdating ? '…' : 'Update'}
                                        </button>
                                    ) : (
                                        <span style={{color: '#aaa', fontSize: 12}}>—</span>
                                    )}
                                </td>
                            )}
                        </tr>
                    );
                })}
                </tbody>
            </table>
        </>
    );
}

const th: React.CSSProperties = {
    padding: '9px 12px',
    textAlign: 'left',
    fontWeight: 600,
    color: '#444',
    borderBottom: '2px solid #c5cdd8',
    whiteSpace: 'nowrap'
};
const td: React.CSSProperties = {padding: '8px 12px', borderBottom: '1px solid #eef1f5'};
const updateBtn: React.CSSProperties = {
    fontSize: 12,
    padding: '3px 10px',
    border: '1px solid #003b5c',
    borderRadius: 3,
    background: 'white',
    color: '#003b5c',
    cursor: 'pointer'
};
const applyBtn: React.CSSProperties = {
    fontSize: 13,
    padding: '6px 18px',
    border: 'none',
    borderRadius: 4,
    background: '#003b5c',
    color: 'white',
    cursor: 'pointer',
    fontWeight: 500
};
const cancelBtn: React.CSSProperties = {
    fontSize: 13,
    padding: '6px 18px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    background: 'white',
    color: '#444',
    cursor: 'pointer'
};
const disabledStyle: React.CSSProperties = {opacity: 0.5, cursor: 'not-allowed'};
const overlay: React.CSSProperties = {
    position: 'fixed',
    inset: 0,
    background: 'rgba(0,0,0,0.35)',
    zIndex: 1000,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center'
};
const dialog: React.CSSProperties = {
    background: 'white',
    borderRadius: 8,
    padding: '24px 28px',
    width: 380,
    boxShadow: '0 8px 30px rgba(0,0,0,0.18)',
    display: 'flex',
    flexDirection: 'column'
};
const labelStyle: React.CSSProperties = {
    fontSize: 12,
    fontWeight: 600,
    color: '#555',
    marginBottom: 5,
    display: 'block'
};
const inputStyle: React.CSSProperties = {
    width: '100%',
    fontSize: 13,
    padding: '7px 10px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    boxSizing: 'border-box',
    fontFamily: 'inherit',
    display: 'block'
};
