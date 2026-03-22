import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { postingApi } from '../api/postingApi';
import type { PostingStatus } from '../types/posting';

// ── Types ──────────────────────────────────────────────────────────────────────

type RangePreset = 'today' | 'this_week' | 'this_month' | 'this_quarter' | 'this_year' | 'custom';
type DateRange = { fromDate?: string; toDate?: string };

const TARGET_SYSTEMS = ['CBS', 'GL', 'OBPM'] as const;

const TARGET_COLORS: Record<string, string> = {
  CBS:  '#1e3a5f',
  GL:   '#1a4731',
  OBPM: '#5f2a1e',
};

const SOURCE_COLORS = [
  '#2d3a8c', '#1a5c4a', '#6b2d8c', '#7a3d1a',
  '#1a4a6b', '#5c3a1a', '#2d6b4a', '#8c2d4a',
];

// ── Date range helpers ─────────────────────────────────────────────────────────

function getPresetRange(preset: RangePreset): DateRange {
  const now = new Date();
  const fmt = (d: Date) => d.toISOString().slice(0, 10);
  const today = fmt(now);
  if (preset === 'today')        return { fromDate: today, toDate: today };
  if (preset === 'this_week') {
    const mon = new Date(now);
    mon.setDate(now.getDate() - ((now.getDay() + 6) % 7));
    return { fromDate: fmt(mon), toDate: today };
  }
  if (preset === 'this_month')   return { fromDate: fmt(new Date(now.getFullYear(), now.getMonth(), 1)), toDate: today };
  if (preset === 'this_quarter') return { fromDate: fmt(new Date(now.getFullYear(), Math.floor(now.getMonth() / 3) * 3, 1)), toDate: today };
  if (preset === 'this_year')    return { fromDate: fmt(new Date(now.getFullYear(), 0, 1)), toDate: today };
  return {};
}

// ── Root page ──────────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const [preset, setPreset]     = useState<RangePreset>('this_month');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo]     = useState('');

  const range: DateRange =
    preset === 'custom'
      ? { fromDate: customFrom || undefined, toDate: customTo || undefined }
      : getPresetRange(preset);

  // Derive distinct source names from posting_config
  const { data: configs } = useQuery({
    queryKey: ['configs'],
    queryFn: postingApi.getAllConfigs,
    staleTime: 5 * 60_000,
  });
  const sourceSystems = [...new Set((configs ?? []).map(c => c.sourceName))].sort();
  const targetPanels = TARGET_SYSTEMS.map((name, i) => ({ name, color: TARGET_COLORS[name] ?? SOURCE_COLORS[i % SOURCE_COLORS.length] }));
  const sourcePanels = sourceSystems.map((name, i) => ({ name, color: SOURCE_COLORS[i % SOURCE_COLORS.length] }));

  return (
    <div style={s.page}>

      {/* ── Header ── */}
      <div style={s.header}>
        <h2 style={s.title}>Dashboard</h2>

        <div style={s.rangeBar}>
          <select
            style={s.rangeSelect}
            value={preset}
            onChange={e => setPreset(e.target.value as RangePreset)}
          >
            <option value="today">Today</option>
            <option value="this_week">This Week</option>
            <option value="this_month">This Month</option>
            <option value="this_quarter">This Quarter</option>
            <option value="this_year">This Year</option>
            <option value="custom">Custom Range</option>
          </select>

          {preset === 'custom' && (
            <>
              <input type="date" style={s.dateInput} value={customFrom} onChange={e => setCustomFrom(e.target.value)} />
              <span style={s.dateSep}>–</span>
              <input type="date" style={s.dateInput} value={customTo}   onChange={e => setCustomTo(e.target.value)} />
            </>
          )}

          {preset !== 'custom' && range.fromDate && (
            <span style={s.rangeLabel}>{range.fromDate} – {range.toDate}</span>
          )}
        </div>
      </div>

      {/* ── By Source System ── */}
      <div style={s.sectionHeader}>By Source System</div>
      {sourcePanels.length === 0
        ? <div style={s.empty}>Loading source systems…</div>
        : (
          <div style={s.grid}>
            {sourcePanels.map(p => (
              <SystemPanel key={p.name} name={p.name} color={p.color} filterKey="sourceName" range={range} />
            ))}
          </div>
        )
      }

      {/* ── By Target System ── */}
      <div style={{ ...s.sectionHeader, marginTop: 32 }}>By Target System</div>
      <div style={s.grid}>
        {targetPanels.map(p => (
          <SystemPanel key={p.name} name={p.name} color={p.color} filterKey="targetSystem" range={range} />
        ))}
      </div>
    </div>
  );
}

// ── Single panel ───────────────────────────────────────────────────────────────

interface PanelProps {
  name: string;
  color: string;
  filterKey: 'targetSystem' | 'sourceName';
  range: DateRange;
}

function SystemPanel({ name, color, filterKey, range }: PanelProps) {
  const baseParams = { [filterKey]: name, size: 1, page: 0, ...range };

  const total   = useStatusCount({ ...baseParams });
  const pending = useStatusCount({ ...baseParams, status: 'PENDING' });
  const success = useStatusCount({ ...baseParams, status: 'SUCCESS' });
  const failed  = useStatusCount({ ...baseParams, status: 'FAILED'  });

  return (
    <div style={s.panel}>
      <div style={{ ...s.panelHeader, background: color }}>
        <span style={s.panelTitle}>{name}</span>
        <span style={s.panelTotal}>{total ?? '…'} total</span>
      </div>
      <div style={s.counters}>
        <Counter label="PENDING" value={pending} color="#856404" bg="#fffbeb" />
        <Counter label="SUCCESS" value={success} color="#0a3622" bg="#f0fdf4" />
        <Counter label="FAILED"  value={failed}  color="#58151c" bg="#fff1f2" />
      </div>
    </div>
  );
}

// ── Hook: fetch only totalElements for one status ──────────────────────────────

function useStatusCount(params: Record<string, unknown>): number | undefined {
  const { data } = useQuery({
    queryKey: ['dashboard-count', params],
    queryFn: () => postingApi.search(params as never),
    staleTime: 30_000,
  });
  return data?.totalElements;
}

// ── Counter tile ───────────────────────────────────────────────────────────────

function Counter({ label, value, color, bg }: {
  label: PostingStatus; value: number | undefined; color: string; bg: string;
}) {
  return (
    <div style={{ ...s.counter, background: bg, color }}>
      <div style={s.counterValue}>{value ?? '—'}</div>
      <div style={s.counterLabel}>{label}</div>
    </div>
  );
}

// ── Styles ─────────────────────────────────────────────────────────────────────

const s: Record<string, React.CSSProperties> = {
  page: {
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
    color: '#222',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
    flexWrap: 'wrap',
    gap: 12,
  },
  title: {
    margin: 0,
    fontSize: 22,
    fontWeight: 600,
    color: '#1a2a3a',
  },
  sectionHeader: {
    fontSize: 13,
    fontWeight: 700,
    color: '#444',
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    marginBottom: 14,
    paddingBottom: 6,
    borderBottom: '2px solid #dde2ea',
  },
  rangeBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  rangeSelect: {
    padding: '6px 10px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    fontSize: 13,
    background: 'white',
    cursor: 'pointer',
  },
  dateInput: {
    padding: '5px 10px',
    border: '1px solid #c5cdd8',
    borderRadius: 4,
    fontSize: 13,
    background: 'white',
  },
  dateSep: {
    color: '#888',
    fontSize: 14,
  },
  rangeLabel: {
    fontSize: 12,
    color: '#666',
    background: '#f4f6f9',
    border: '1px solid #dde2ea',
    borderRadius: 4,
    padding: '4px 10px',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))',
    gap: 20,
  },
  panel: {
    border: '1px solid #dde2ea',
    borderRadius: 8,
    overflow: 'hidden',
    background: 'white',
  },
  panelHeader: {
    padding: '14px 16px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  panelTitle: {
    color: 'white',
    fontWeight: 700,
    fontSize: 16,
    letterSpacing: 0.5,
  },
  panelTotal: {
    color: 'rgba(255,255,255,0.75)',
    fontSize: 12,
  },
  counters: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr',
  },
  counter: {
    padding: '14px 8px',
    textAlign: 'center',
    borderRight: '1px solid #eef1f5',
  },
  counterValue: {
    fontSize: 26,
    fontWeight: 700,
    lineHeight: 1,
  },
  counterLabel: {
    fontSize: 10,
    fontWeight: 600,
    marginTop: 4,
    letterSpacing: 0.5,
  },
  empty: {
    textAlign: 'center',
    padding: 40,
    color: '#888',
    fontSize: 14,
  },
};
