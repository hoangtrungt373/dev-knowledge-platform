/**
 * Scans a native paste event's own `DataTransferItemList` for the first image item and returns
 * it as a `File`, or `null` if the clipboard held no image — `Base64ImagePanel.tsx`'s own
 * `handlePaste` and `QrCodePanel.tsx`'s own `handleDropzonePaste` each ran this exact indexed-loop
 * scan independently, so an image pasted directly (e.g. Ctrl+V of a screenshot) takes the same
 * `handleFile` path a real upload does in both panels.
 *
 * <p>A plain indexed loop, not `for...of` — `DataTransferItemList`'s own TS typings don't guarantee
 * an iterator the way `FileList`'s do, so a `for...of` here risks a downlevelIteration error
 * depending on this project's own `tsconfig` target.
 */
export function extractImageFileFromClipboardEvent(items: DataTransferItemList | null | undefined): File | null {
  if (!items) return null;
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile();
      if (file) return file;
    }
  }
  return null;
}

/**
 * The explicit "Paste" button's own async clipboard read — `Base64ImagePanel.tsx`'s and
 * `QrCodePanel.tsx`'s own `handlePasteButtonClick` each ran this exact
 * `navigator.clipboard.read()` scan independently (a real click is a user gesture, so the
 * image-capable `read()` API is available here the same way it would be from a keyboard Ctrl+V,
 * unlike the text-only `readText()` `usePasteText` uses elsewhere in this feature). Returns the
 * first image item found as a `File` (named `pasted.<extension>`, `extension` resolved from
 * `allowedTypes`, falling back to `png` for a mime type not in that map), or `null` when the
 * clipboard holds no image at all — including when `navigator.clipboard.read` isn't available in
 * this browser, so the caller can fall back to `readText()` for a plain string instead.
 */
export async function readImageFromClipboard(allowedTypes: Record<string, string>): Promise<File | null> {
  if (!navigator.clipboard.read) {
    return null;
  }
  const items = await navigator.clipboard.read();
  for (const item of items) {
    const imageType = item.types.find(type => type.startsWith('image/'));
    if (imageType) {
      const blob = await item.getType(imageType);
      const extension = allowedTypes[imageType] ?? 'png';
      return new File([blob], `pasted.${extension}`, { type: imageType });
    }
  }
  return null;
}
