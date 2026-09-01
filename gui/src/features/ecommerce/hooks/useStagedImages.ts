import { useEffect, useRef, useState } from 'react';
import { StagedImage } from '../components/ProductImageStager';

export interface UseStagedImagesResult {
  stagedImages: StagedImage[];
  addStagedImage: (file: File) => void;
  removeStagedImage: (imageId: string) => void;
  reorderStagedImage: (imageId: string, direction: -1 | 1) => void;
  /** Revokes every currently-queued preview blob URL — call once the images are either uploaded
   * or abandoned (a successful submit). Unmount (Cancel, back button, navigating away without
   * submitting) is handled automatically by this hook's own cleanup effect below; this is only
   * for the success path, which doesn't unmount immediately (edit mode navigates to the same
   * page's own edit route instead). */
  revokeAll: () => void;
}

/**
 * Create-mode-only images queued locally (no network call yet — see `ProductImageStager`'s own
 * Javadoc for why this can't just reuse `ProductImageGallery`). `ProductFormPage`'s `handleSubmit`
 * is what actually uploads these, one `ecommerceApi.uploadImage(newProductId, ...)` call per file,
 * immediately after `createProduct` returns the new id. Extracted out of `ProductFormPage.tsx`
 * alongside `useProductTags`/`useDraftVariants` — see that hook's own doc comment for the
 * God-Component context.
 */
export function useStagedImages(): UseStagedImagesResult {
  const [stagedImages, setStagedImages] = useState<StagedImage[]>([]);
  const stagedImageIdCounter = useRef(0);
  // Mirrors stagedImages on every render (no effect needed just to keep a ref in sync) so the
  // unmount-cleanup effect below always revokes the *current* set of blob URLs, not whatever was
  // staged at mount time — a plain `[]`-deps cleanup closure would otherwise only ever see the
  // empty array from the very first render.
  const stagedImagesRef = useRef<StagedImage[]>([]);
  stagedImagesRef.current = stagedImages;

  // Revokes any still-queued preview blob URLs if the admin navigates away (Cancel, back button)
  // without ever submitting — the success path calls revokeAll explicitly instead, since by then
  // they're either uploaded or abandoned either way.
  useEffect(() => {
    return () => {
      stagedImagesRef.current.forEach(img => URL.revokeObjectURL(img.previewUrl));
    };
  }, []);

  const addStagedImage = (file: File): void => {
    stagedImageIdCounter.current += 1;
    setStagedImages(prev => [...prev, {
      id: `staged-${stagedImageIdCounter.current}`,
      file,
      previewUrl: URL.createObjectURL(file),
    }]);
  };

  const removeStagedImage = (imageId: string): void => {
    setStagedImages(prev => {
      const target = prev.find(img => img.id === imageId);
      if (target) URL.revokeObjectURL(target.previewUrl);
      return prev.filter(img => img.id !== imageId);
    });
  };

  const reorderStagedImage = (imageId: string, direction: -1 | 1): void => {
    setStagedImages(prev => {
      const index = prev.findIndex(img => img.id === imageId);
      const otherIndex = index + direction;
      if (index === -1 || otherIndex < 0 || otherIndex >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[otherIndex]] = [next[otherIndex], next[index]];
      return next;
    });
  };

  const revokeAll = (): void => {
    stagedImages.forEach(img => URL.revokeObjectURL(img.previewUrl));
  };

  return { stagedImages, addStagedImage, removeStagedImage, reorderStagedImage, revokeAll };
}
