export interface UnixTimeFields {
  dateTime: string;
  dateTimeZoneId: string;
  timestamp: string;
  timestampZoneId: string;
}

const EMPTY_FIELDS: UnixTimeFields = { dateTime: '', dateTimeZoneId: '', timestamp: '', timestampZoneId: '' };

/**
 * Serializes `UnixTimeConverterPanel.tsx`'s own 4 fields (2 per direction: a value plus its own
 * zone id) into the single lifted `input` string `DevUtilsPage.tsx`'s Sample/Clear buttons
 * already operate on — the same "one shared lifted string, several visual widgets" trick
 * `utils/regexInputFormat.ts`/`utils/textDiffInputFormat.ts` already establish for their own
 * multi-field panels. Plain `JSON.stringify`, not a custom delimiter line — unlike those two
 * files' own free-text fields (a test string, a whole diffed file), none of these 4 fields can
 * ever contain a newline or any other character JSON needs to escape (a `datetime-local` input's
 * own value, an IANA zone id, a numeric timestamp string), so there's no real separator-collision
 * risk here to design around, and JSON is simpler than inventing one.
 */
export function serializeUnixTimeInput(fields: UnixTimeFields): string {
  return JSON.stringify(fields);
}

/** Tolerant of anything that isn't valid JSON (e.g. `DevUtilsPage.tsx`'s Clear button setting
 * `input` to `''`) — falls back to every field blank rather than throwing, the same tolerant-
 * fallback convention `parseTextDiffInput` already establishes for its own blank/Clear case. */
export function parseUnixTimeInput(raw: string): UnixTimeFields {
  if (!raw) {
    return EMPTY_FIELDS;
  }
  try {
    const parsed = JSON.parse(raw);
    return {
      dateTime: typeof parsed.dateTime === 'string' ? parsed.dateTime : '',
      dateTimeZoneId: typeof parsed.dateTimeZoneId === 'string' ? parsed.dateTimeZoneId : '',
      timestamp: typeof parsed.timestamp === 'string' ? parsed.timestamp : '',
      timestampZoneId: typeof parsed.timestampZoneId === 'string' ? parsed.timestampZoneId : '',
    };
  } catch {
    return EMPTY_FIELDS;
  }
}
