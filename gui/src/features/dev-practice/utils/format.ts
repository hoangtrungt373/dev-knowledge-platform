/**
 * Date formatting for every dev-practice page, so the same kind of timestamp reads the same
 * everywhere. Each helper renders `—` for a missing value (e.g. a draft's `publishedAt`).
 */

/** A calendar date — "Oct 8, 2026". For list columns. */
export function formatDate(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
}

/** A judge-measured runtime — "12 ms". */
export function formatRuntime(ms: number): string {
  return `${ms} ms`;
}

/** A judge-measured peak memory, given in KB — "40.2 MB", or "512 KB" below one megabyte. */
export function formatMemory(kb: number): string {
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}

/**
 * "Runtime 12 ms · Memory 40.2 MB" for a submission that has them (ACCEPTED ones only), else null.
 * Either half is left out if only the other was measured.
 */
export function submissionStats(s: { runtimeMs: number | null; memoryKb: number | null }): string | null {
  const parts = [
    s.runtimeMs != null ? `Runtime ${formatRuntime(s.runtimeMs)}` : null,
    s.memoryKb != null ? `Memory ${formatMemory(s.memoryKb)}` : null,
  ].filter((p): p is string => p !== null);
  return parts.length > 0 ? parts.join(' · ') : null;
}

/** A date and time — "Oct 8, 2026, 14:05". For detail panels and submission histories. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
