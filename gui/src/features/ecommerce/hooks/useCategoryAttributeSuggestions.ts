import { useEffect, useMemo, useState } from 'react';
import { ecommerceApi } from '../api/ecommerceApi';
import { ProductAttribute, ProductCategory } from '../types';

/** One category-assigned attribute, resolved from ids to a name/controlled-vocabulary — ready to
 * render as a suggested attribute row in `ProductVariantDialog`. */
export interface SuggestedAttribute {
  attributeId: number;
  /** Matched literally against a `ProductVariant.attributes` map key — see `ProductAttribute`'s
   * own note in `types.ts`. */
  name: string;
  required: boolean;
  /** This attribute's controlled vocabulary, in display order. */
  values: string[];
}

/**
 * Resolves the given product category's own attribute schema ("Option B" global attribute
 * registry — see `ecommerce-service/CLAUDE.md`'s `ProductAttribute` note) into a ready-to-render
 * suggestion list, in display order. Empty when the category has no schema at all (fully
 * free-form — today's pre-"Option B" behavior, and every category before this feature existed)
 * or when no category is selected yet (`categoryId === ''`).
 *
 * Fetches the full category list (needed for `category.attributes`, ids only — see
 * `CategoryAttributeAssignment`'s own note in `types.ts`) and the full attribute registry (needed
 * to resolve each id to a name/vocabulary) once, independent of which category is currently
 * selected — the same "just fetch everything for a picker" convention
 * `ProductCategoryFormDialog.tsx`'s own Attributes section already uses. Both fetches fail
 * silently (no `showError`) — this only pre-fills a helper UI, not a real error worth a toast,
 * same reasoning that dialog's own attribute fetch already documents.
 */
export function useCategoryAttributeSuggestions(categoryId: number | ''): SuggestedAttribute[] {
  const [categories, setCategories] = useState<ProductCategory[]>([]);
  const [attributes, setAttributes] = useState<ProductAttribute[]>([]);

  useEffect(() => {
    ecommerceApi.listProductCategories().then(setCategories).catch(() => {
      // Silent — the suggested-attributes section just falls back to free-form editing.
    });
    ecommerceApi.listProductAttributes({ page: 0, size: 200, sortBy: 'name', sortDir: 'asc' })
      .then(result => setAttributes(result.content))
      .catch(() => {
        // Silent — same reasoning as above.
      });
  }, []);

  return useMemo(() => {
    if (categoryId === '') return [];
    const category = categories.find(c => c.id === categoryId);
    if (!category) return [];
    return category.attributes
      .slice()
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .map((assignment): SuggestedAttribute => {
        const attribute = attributes.find(a => a.id === assignment.attributeId);
        return {
          attributeId: assignment.attributeId,
          name: attribute?.name ?? `#${assignment.attributeId}`,
          required: assignment.required,
          values: attribute
            ? attribute.values.slice().sort((a, b) => a.displayOrder - b.displayOrder).map(v => v.value)
            : [],
        };
      });
  }, [categoryId, categories, attributes]);
}
