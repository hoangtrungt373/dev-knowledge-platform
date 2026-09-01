import { useEffect, useState } from 'react';

/**
 * Returns `value`, updated only once it has stayed unchanged for `delayMs` — the debounced
 * search-input pattern (`setTimeout(() => setSearch(searchInput), 300)` + cleanup) that had been
 * duplicated near-identically across `ProductCategoryListPage`/`ProductTagListPage`/
 * `ProductListPage`/`ShopPage`. A caller that also needs to reset pagination on change still does
 * that itself, in its own effect keyed on the returned (debounced) value — this hook only owns
 * the debouncing itself.
 *
 * Usage:
 *   const [searchInput, setSearchInput] = useState('');
 *   const search = useDebouncedValue(searchInput, 300);
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);

  return debounced;
}
