type QueryValue = string | number | boolean | undefined | null;

/** The shape `buildQueryString` accepts. A plain object *literal* (`{ page, size }`) is checked
 * against this directly with no cast needed — but a value already typed via a named `interface`
 * (e.g. this feature's own `ProductListParams`) needs an explicit `as QueryParams` at the call
 * site: TypeScript infers an implicit index signature for object-literal types, but never for
 * `interface`-declared ones, so only the latter fails the assignability check without a cast. */
export type QueryParams = Record<string, QueryValue | QueryValue[]>;

/**
 * Builds a `?a=1&b=2` query string from a flat params object — scalar values are set once, array
 * values are appended as repeated keys (`?tagIds=1&tagIds=2`), matching every `@RequestParam
 * Set<T>`/`List<T>` binding across this reactor's backend services. `undefined`/`null`/`''`
 * values (scalar, or inside an array) are omitted entirely rather than serialized as the literal
 * string `"undefined"`. Returns `''` (not `'?'`) when nothing is left to serialize.
 *
 * Extracted once a near-identical `buildQuery` had been copy-pasted into `ecommerceApi.ts` and
 * `shopApi.ts`, alongside three more one-off repeated-query-param builders in `orderApi.ts`
 * (`statuses`), `checkoutApi.ts` (`selectedVariantIds`), and `adminOrderApi.ts` (`status`) — five
 * different hand-rolled implementations of the same need.
 */
export function buildQueryString(params: QueryParams): string {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    const values = Array.isArray(value) ? value : [value];
    values.forEach(v => {
      if (v !== undefined && v !== null && v !== '') q.append(key, String(v));
    });
  });
  const s = q.toString();
  return s ? `?${s}` : '';
}
