/**
 * Date formatting for every dev-practice page, so the same kind of timestamp reads the same
 * everywhere. Each helper renders `—` for a missing value (e.g. a draft's `publishedAt`).
 */

/** A calendar date — "Oct 8, 2026". For list columns. */
export function formatDate(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
}

/** A date and time — "Oct 8, 2026, 14:05". For detail panels and submission histories. */
export function formatDateTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
