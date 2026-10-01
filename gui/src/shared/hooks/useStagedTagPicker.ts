import { useEffect, useRef, useState } from 'react';

/** The minimum a tag needs for the picker — `ProductTag` and `ProblemTag` both fit. */
export interface PickableTag {
  id: number;
  name: string;
}

export interface StagedTagPickerOptions<T extends PickableTag> {
  /** Loads the whole tag catalog once (pickers aren't paginated — catalogs are small). */
  loadTags: () => Promise<T[]>;
  /** Creates one tag — only ever called from `resolveStagedTagIds`, i.e. at submit time. */
  createTag: (name: string) => Promise<T>;
}

export interface StagedTagPickerResult<T extends PickableTag> {
  allTags: T[];
  selectedTagIds: Set<number>;
  /** Seeds the selection from a freshly-loaded record's own tags — the hook has no notion of what
   * is being tagged. */
  setSelectedTagIds: (ids: Set<number>) => void;
  stagedTagNames: string[];
  newTagInput: string;
  setNewTagInput: (value: string) => void;
  toggleTag: (tagId: number) => void;
  handleAddStagedTag: () => void;
  handleRemoveStagedTag: (tagName: string) => void;
  /** Actually creates every staged tag name — call this from a submit handler only, right before
   * the owning record itself is created/updated, so nothing lands in the tag catalog until the save
   * is actually attempted. Aborts (rethrows) on the first failure rather than continuing
   * best-effort: an incomplete tag set silently applied to the record would be more surprising than
   * a failed save the admin can simply retry. Does *not* clear `stagedTagNames` — call
   * `clearStagedTagNames` once the caller has actually used the returned ids. */
  resolveStagedTagIds: () => Promise<number[]>;
  clearStagedTagNames: () => void;
}

/**
 * The tag picker's full state/logic, shared by every form that tags a record with a catalog of
 * tags (`@ecommerce`'s product form, `@dev-practice`'s problem form): the whole catalog (loaded
 * once), the current selection among real tags, and a separate "New tags" queue of names typed
 * but not yet created. Generic over the tag type, with loading/creation injected by the caller, so
 * it knows nothing about which feature's API it talks to. Renders nothing — pair it with
 * `@shared/components/TagPicker`.
 *
 * Moved here from `@ecommerce/hooks/useProductTags` (now a thin wrapper over this) once the problem
 * form needed the identical behavior.
 */
export function useStagedTagPicker<T extends PickableTag>(options: StagedTagPickerOptions<T>): StagedTagPickerResult<T> {
  const [allTags, setAllTags] = useState<T[]>([]);
  const [selectedTagIds, setSelectedTagIds] = useState<Set<number>>(new Set());
  // Brand-new tag names typed into the form but not yet persisted — plain strings, not tags with
  // a real id. Kept separate from allTags/selectedTagIds rather than optimistically inserted into
  // them, since a discarded form (Cancel, navigate away) must leave zero trace in the catalog.
  const [stagedTagNames, setStagedTagNames] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState('');

  // The callers pass inline arrow functions, which are new on every render. Reading them through a
  // ref keeps the load effect below from re-running (and refetching) on every render, while
  // resolveStagedTagIds still always calls the latest createTag.
  const optionsRef = useRef(options);
  optionsRef.current = options;

  useEffect(() => {
    optionsRef.current.loadTags().then(setAllTags).catch(() => {
      // The caller's loader already reports its own errors (e.g. via showError).
    });
  }, []);

  const toggleTag = (tagId: number): void => {
    setSelectedTagIds(prev => {
      const next = new Set(prev);
      if (next.has(tagId)) next.delete(tagId);
      else next.add(tagId);
      return next;
    });
  };

  // Queues a brand-new tag name locally — no API call here at all. A name that already matches an
  // existing tag (case-insensitively) selects that tag instead of queuing a duplicate that would
  // only fail with a name-conflict error once the form is saved; an already-queued duplicate is
  // likewise a silent no-op.
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
      const created = await optionsRef.current.createTag(tagName);
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
