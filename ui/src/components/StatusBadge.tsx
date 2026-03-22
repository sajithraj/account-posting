import type { PostingStatus } from '../types/posting';

const colours: Record<PostingStatus, { bg: string; text: string }> = {
  PENDING: { bg: '#fff3cd', text: '#856404' },
  SUCCESS: { bg: '#d1e7dd', text: '#0a3622' },
  FAILED:  { bg: '#f8d7da', text: '#58151c' },
};

export default function StatusBadge({ status }: { status: PostingStatus }) {
  const { bg, text } = colours[status] ?? { bg: '#e9ecef', text: '#495057' };
  return (
    <span style={{ padding: '2px 8px', borderRadius: 12, fontSize: 12, fontWeight: 600, backgroundColor: bg, color: text }}>
      {status}
    </span>
  );
}
