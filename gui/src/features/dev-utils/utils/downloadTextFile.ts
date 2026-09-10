/**
 * Triggers a browser download of `content` as a plain text file named `fileName` — a `Blob` →
 * `URL.createObjectURL` → temporary `<a download>` click → `URL.revokeObjectURL` round trip.
 * Extracted once `RegExpTesterPanel.tsx` needed the identical helper `DevUtilToolPanel.tsx`
 * already had defined locally (module-private, not exported) — no existing helper anywhere else
 * in this app did this either.
 */
export function downloadTextFile(fileName: string, content: string): void {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}
