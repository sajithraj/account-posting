import { useState } from 'react';
import type { LegResponse } from '../types/posting';

interface Props {
  legs: LegResponse[];
  /** When provided, a "Mark" action column is shown for non-SUCCESS legs. */
  onUpdateStatus?: (postingLegId: number, status: string) => void;
  updatingLegId?: number | null;
}

export default function LegTable({ legs, onUpdateStatus, updatingLegId }: Props) {
  const [pendingStatus, setPendingStatus] = useState<Record<number, string>>({});

  if (legs.length === 0) {
    return <p style={{ color: '#888' }}>No legs found for this posting.</p>;
  }

  const showActions = !!onUpdateStatus;

  return (
    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
      <thead>
        <tr style={{ background: '#eef1f5' }}>
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
          const isUpdating = updatingLegId === leg.postingLegId;
          const selected = pendingStatus[leg.postingLegId] ?? leg.status;

          return (
            <tr key={i}>
              <td style={{ ...td, textAlign: 'center', fontWeight: 600 }}>{leg.legOrder}</td>
              <td style={td}>{leg.name}</td>
              <td style={td}>{leg.type}</td>
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
                <td style={{ ...td, whiteSpace: 'nowrap' }}>
                  {!isSuccess ? (
                    <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                      <select
                        style={selectStyle}
                        value={selected}
                        disabled={isUpdating || !leg.postingLegId}
                        onChange={e => {
                          if (!leg.postingLegId) return;
                          setPendingStatus(prev => ({ ...prev, [leg.postingLegId]: e.target.value }));
                        }}
                      >
                        <option value="SUCCESS">SUCCESS</option>
                        <option value="FAILED">FAILED</option>
                      </select>
                      <button
                        style={{ ...applyBtn, ...((isUpdating || !leg.postingLegId) ? disabledStyle : {}) }}
                        disabled={isUpdating || !leg.postingLegId || selected === leg.status}
                        onClick={() => { if (leg.postingLegId) onUpdateStatus!(leg.postingLegId, selected); }}
                      >
                        {isUpdating ? '…' : 'Apply'}
                      </button>
                    </div>
                  ) : (
                    <span style={{ color: '#aaa', fontSize: 12 }}>—</span>
                  )}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

const th: React.CSSProperties = { padding: '9px 12px', textAlign: 'left', fontWeight: 600, color: '#444', borderBottom: '2px solid #c5cdd8', whiteSpace: 'nowrap' };
const td: React.CSSProperties = { padding: '8px 12px', borderBottom: '1px solid #eef1f5' };
const selectStyle: React.CSSProperties = { fontSize: 12, padding: '2px 4px', border: '1px solid #c5cdd8', borderRadius: 3, background: 'white' };
const applyBtn: React.CSSProperties = { fontSize: 12, padding: '2px 8px', border: '1px solid #003b5c', borderRadius: 3, background: 'white', color: '#003b5c', cursor: 'pointer' };
const disabledStyle: React.CSSProperties = { opacity: 0.5, cursor: 'not-allowed' };
