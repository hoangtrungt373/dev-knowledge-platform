import { useRef, useState } from 'react';
import { DisplayVariant } from '../components/ProductVariantEditor';
import { ProductVariantInput } from '../types';

export interface UseDraftVariantsResult {
  draftVariants: DisplayVariant[];
  addDraftVariant: (input: ProductVariantInput) => void;
  removeDraftVariant: (variantId: number | string) => void;
}

/**
 * Create-mode-only local, unsaved variant list — a product requires >=1 variant to exist at all
 * (US-1.6), so these travel in the same create request as the basic fields, unlike edit mode
 * where variants are added/removed independently against a real product (see `ProductFormPage`'s
 * own `handleAddLiveVariant`/`handleRemoveLiveVariant`, which stay inline — they're a couple of
 * lines each and already talk straight to `ecommerceApi`, nothing to extract). Extracted out of
 * `ProductFormPage.tsx` alongside `useProductTags`/`useStagedImages` — see that hook's own doc
 * comment for the God-Component context.
 */
export function useDraftVariants(): UseDraftVariantsResult {
  const [draftVariants, setDraftVariants] = useState<DisplayVariant[]>([]);
  const draftIdCounter = useRef(0);

  const addDraftVariant = (input: ProductVariantInput): void => {
    draftIdCounter.current += 1;
    setDraftVariants(prev => [...prev, {
      id: `draft-${draftIdCounter.current}`,
      sku: input.sku,
      price: input.price,
      stockQuantity: input.stockQuantity,
      attributes: input.attributes ?? {},
    }]);
  };

  const removeDraftVariant = (variantId: number | string): void => {
    setDraftVariants(prev => prev.filter(v => v.id !== variantId));
  };

  return { draftVariants, addDraftVariant, removeDraftVariant };
}
