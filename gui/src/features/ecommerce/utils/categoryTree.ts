import { ProductCategoryTreeNode } from '../types';

/** One category flattened out of a `ProductCategoryTreeNode[]`, annotated with its nesting depth (0 = root) so a picker can indent it. */
export interface FlatCategoryOption {
  id: number;
  name: string;
  depth: number;
}

/**
 * Depth-first flattens a category tree into a flat, indent-ready list — root categories first,
 * each immediately followed by its own children before the next root, matching how
 * `ecommerceApi.getProductCategoryTree` orders its nodes (each level already sorted by name).
 * Shared by `ProductCategoryFormDialog.tsx`'s parent-picker and `ProductFormPage.tsx`'s own
 * category `Select` — both need the identical "one row per category, indented by depth" shape,
 * just with a different (or no) `excludeIds` set.
 *
 * @param excludeIds category ids to omit, along with their entire subtree — used by the parent-
 *   picker to keep a category from being assigned as its own descendant's parent; unused
 *   (defaults to empty) by a plain "pick this product's category" list, which has no such concern.
 */
export function flattenCategoryTree(
  nodes: ProductCategoryTreeNode[],
  depth = 0,
  excludeIds: Set<number> = new Set(),
): FlatCategoryOption[] {
  const result: FlatCategoryOption[] = [];
  for (const node of nodes) {
    if (excludeIds.has(node.id)) continue;
    result.push({ id: node.id, name: node.name, depth });
    result.push(...flattenCategoryTree(node.children, depth + 1, excludeIds));
  }
  return result;
}
