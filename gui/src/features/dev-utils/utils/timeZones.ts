// A reasonably broad fallback list for a browser without `Intl.supportedValuesOf` (Safari < 15.4,
// older Firefox/Chrome — every evergreen browser has supported it since early 2022) — one
// representative zone per major region/offset, not exhaustive; when the real API is available
// (the common case), `listSupportedTimeZones` below returns the full, real IANA list instead of
// this.
const FALLBACK_TIME_ZONES = [
  'UTC',
  'Pacific/Midway',
  'Pacific/Honolulu',
  'America/Anchorage',
  'America/Los_Angeles',
  'America/Denver',
  'America/Chicago',
  'America/New_York',
  'America/Sao_Paulo',
  'Atlantic/Azores',
  'Europe/London',
  'Europe/Paris',
  'Europe/Berlin',
  'Europe/Athens',
  'Europe/Moscow',
  'Africa/Cairo',
  'Africa/Johannesburg',
  'Asia/Jerusalem',
  'Asia/Dubai',
  'Asia/Karachi',
  'Asia/Kolkata',
  'Asia/Dhaka',
  'Asia/Bangkok',
  'Asia/Ho_Chi_Minh',
  'Asia/Jakarta',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Asia/Seoul',
  'Australia/Perth',
  'Australia/Sydney',
  'Pacific/Auckland',
];

let cached: string[] | null = null;

/** Every IANA zone id the current browser's own ICU data knows about, sorted alphabetically —
 * `Intl.supportedValuesOf('timeZone')` is the real source (not yet in this project's own
 * `tsconfig.json` `lib` target, hence the local cast rather than a global `lib` bump just for
 * this one call), falling back to a small curated list for the rare browser without it.
 * Memoized module-level — the underlying ICU data can't change within a page load, so there's no
 * reason to recompute/re-sort this on every call. */
export function listSupportedTimeZones(): string[] {
  if (cached) {
    return cached;
  }
  try {
    const supportedValuesOf = (Intl as unknown as { supportedValuesOf?: (key: string) => string[] })
      .supportedValuesOf;
    if (supportedValuesOf) {
      cached = supportedValuesOf('timeZone').slice().sort();
      return cached;
    }
  } catch {
    // Falls through to the fallback list below.
  }
  cached = [...FALLBACK_TIME_ZONES].sort();
  return cached;
}
