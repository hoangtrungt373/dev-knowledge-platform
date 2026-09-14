/** A generous but finite cap, purely a client-side UX safeguard — neither `Base64ImagePanel.tsx`
 * nor `QrCodePanel.tsx` round-trips the file through a backend endpoint at all, so there's no
 * server-side `MAX_INPUT_LENGTH`-style limit to lean on instead. Both panels use this exact value
 * today; a panel with a genuine reason to allow something larger/smaller can still pass its own
 * number to {@link validateImageFile} directly rather than being forced onto this one. */
export const DEFAULT_MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;

export function formatBytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * The "is this file an allowed image type, and is it under the size cap" check both
 * `Base64ImagePanel.tsx`'s and `QrCodePanel.tsx`'s own `handleFile` used to run independently,
 * word-for-word identical down to the error message text — only what happens *after* validation
 * passes differs between the two (one just sets the Data URL as `input`; the other decodes it as a
 * QR code). Returns the user-facing error message to show (via `showError`) instead of throwing,
 * or `null` when the file is fine to proceed with — the caller decides how to react either way.
 */
export function validateImageFile(
  file: File,
  allowedTypes: Record<string, string>,
  allowedTypesLabel: string,
  maxSizeBytes: number
): string | null {
  if (!(file.type in allowedTypes)) {
    return `"${file.name}" isn't a supported image type — allowed: ${allowedTypesLabel}.`;
  }
  if (file.size > maxSizeBytes) {
    return `"${file.name}" is ${formatBytes(file.size)} — the limit is ${formatBytes(maxSizeBytes)}.`;
  }
  return null;
}
