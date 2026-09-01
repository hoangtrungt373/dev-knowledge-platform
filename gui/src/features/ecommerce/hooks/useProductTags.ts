import { useEffect, useState } from 'react';
import { ecommerceApi } from '../api/ecommerceApi';
import { ProductTag } from '../types';

type ShowError = (msg: string) => void;

export interface UseProductTagsResult {
  allTags: ProductTag[];
  selectedTagIds: Set<number>;
  /** Lets a caller (e.g. `ProductFormPage`'s `loadProduct`) seed the current selection from a
   * freshly-loaded product's own `tagIds` — the hook itself has no notion of "a product". */
  setSelectedTagIds: (ids: Set<number>) => void;
  stagedTagNames: string[];
  newTagInput: string;
  setNewTagInput: (value: string) => void;
  toggleTag: (tagId: number) => void;
  handleAddStagedTag: () => void;
  handleRemoveStagedTag: (tagName: string) => void;
  /** Actually creates every staged tag name — call this from a submit handler only, right before
   * the product itself is created/updated, so nothing lands in the tag catalog until the product
   * save is actually attempted. Aborts (rethrows) on the first failure rather than continuing
   * best-effort like `useStagedImages`' own upload loop: an incomplete tag set silently applied to
   * the product would be more surprising here than a failed save the admin can simply retry. Does
   * *not* clear `stagedTagNames` itself — call `clearStagedTagNames` once the caller has actually
   * used the returned ids (mirrors the original inline behavior in `ProductFormPage.handleSubmit`). */
  resolveStagedTagIds: () => Promise<number[]>;
  clearStagedTagNames: () => void;
}

/**
 * The product-tag picker's full state/logic — every product tag (fetched once), the current
 * selection among the real catalog, and a separate "New tags" queue of not-yet-persisted names
 * (see `resolveStagedTagIds`'s own doc comment for why creation is deferred to submit time).
 * Extracted out of `ProductFormPage.tsx` once that file grew into this app's largest God Component
 * — same "extract a custom hook" remedy `gui/CLAUDE.md` already prescribes for
 * `EmbeddingsPage`/`ProfilePage`/`PipelineMetricsPage`/`QuestionAnswerFormPage`.
 */
export function useProductTags(showError: ShowError): UseProductTagsResult {
  const [allTags, setAllTags] = useState<ProductTag[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());
  // Brand-new tag names typed into the form but not yet persisted — nothing is created on the
  // backend until the product itself is saved (see resolveStagedTagIds), so these are plain
  // strings, not ProductTag objects with a real id. Kept separate from allTags/selectedTagIds
  // rather than optimistically inserted into them, since a discarded form (Cancel, navigate away)
  // must leave zero trace in the tag catalog.
  const [stagedTagNames, setStagedTagNames] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState('');

  useEffect(() => {
    ecommerceApi.listProductTags({ size: 1000, sortBy: 'name', sortDir: 'asc' }, showError)
      .then(page => setAllTags(page.content));
  }, [showError]);

  const toggleTag = (tagId: number): void => {
    setSelectedTagIds(prev => {
      const next = new Set(prev);
      next.has(tagId) ? next.delete(tagId) : next.add(tagId);
      return next;
    });
  };

  // Queues a brand-new tag name locally — no API call here at all. If the name already matches an
  // existing catalog tag (case-insensitively), that existing tag is selected instead of queuing a
  // duplicate that would only fail with PRODUCT_TAG_NAME_CONFLICT once the form is actually saved;
  // an already-queued duplicate is likewise a silent no-op rather than a second staged entry.
  const handleAddStagedTag = (): void => {
    const trimmed = newTagInput.trim();
    if (!trimmed) return;

    const existing = allTags.find(t => t.name.toLowerCase() === trimmed.toLowerCase());
    if (existing) {
      setSelectedTagIds(prev => new Set(prev).add(existing.id));
      setNewTagInput('');
      return;
    }
    if (stagedTagNames.some(n => n.toLowerCase() === trimmed.toLowerCase())) {
      setNewTagInput('');
      return;
    }
    setStagedTagNames(prev => [...prev, trimmed]);
    setNewTagInput('');
  };

  const handleRemoveStagedTag = (tagName: string): void => {
    setStagedTagNames(prev => prev.filter(n => n !== tagName));
  };

  const resolveStagedTagIds = async (): Promise<number[]> => {
    const ids: number[] = [];
    for (const tagName of stagedTagNames) {
      const created = await ecommerceApi.createProductTag({ name: tagName }, showError);
      setAllTags(prev => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      ids.push(created.id);
    }
    return ids;
  };

  return {
    allTags,
    selectedTagIds,
    setSelectedTagIds,
    stagedTagNames,
    newTagInput,
    setNewTagInput,
    toggleTag,
    handleAddStagedTag,
    handleRemoveStagedTag,
    resolveStagedTagIds,
    clearStagedTagNames: () => setStagedTagNames([]),
  };
}
