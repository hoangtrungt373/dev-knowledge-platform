import { ProductCategory } from '../types';

/**
 * Resolves the root→leaf ancestor chain for `categoryId` out of a flat category list (the shape
 * `shopApi.listCategories`/`ecommerceApi.listProductCategories` both return — no dedicated
 * "category path" endpoint exists; walking `parentId` client-side is enough now that the flat list
 * carries it). Returns `[]` if `categoryId` isn't in `categories` (e.g. the list hasn't loaded yet).
 *
 * The `seen` guard is defensive only — the backend rejects a cyclic `parentId` assignment
 * (`ProductCategoryServiceImpl.validateParentAssignment`), so a cycle should never actually reach
 * this function, but an infinite loop here would hang the page rather than just mis-render if one
 * ever did.
 */
export function buildCategoryPath(categories: ProductCategory[], categoryId: number): ProductCategory[] {
  const byId = new Map(categories.map(c => [c.id, c]));
  const path: ProductCategory[] = [];
  const seen = new Set<number>();

  let current = byId.get(categoryId);
  while (current && !seen.has(current.id)) {
    seen.add(current.id);
    path.unshift(current);
    current = current.parentId !== null ? byId.get(current.parentId) : undefined;
  }

  return path;
}
