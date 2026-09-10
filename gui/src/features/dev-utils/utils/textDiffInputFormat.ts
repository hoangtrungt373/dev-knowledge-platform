export interface TextDiffFields {
  original: string;
  updated: string;
}

/**
 * The line that separates `original` from `updated` inside the single lifted `input` string
 * `DevUtilsPage.tsx`'s Sample/Clear buttons operate on — the same "one shared lifted string,
 * several visual widgets" trick `RegExpTesterPanel.tsx`'s own `utils/regexInputFormat.ts` already
 * establishes for its own pattern/flags/testText trio. A blank-line separator (RegExp Tester's own
 * choice) isn't safe here — unlike a regex pattern, either text block can legitimately contain a
 * blank line of its own — so this uses a distinctive, git-conflict-marker-styled line instead
 * (deliberately evoking git's own {@code <<<<<<<}/{@code >>>>>>>} conflict markers, fitting for a
 * diff-related tool) that's astronomically unlikely to collide with real content. **Known,
 * accepted limitation**: if either `original` or `updated` ever contains this exact line
 * verbatim, round-tripping through this format would misparse it — a client-side-only,
 * vanishingly rare edge case (this only affects the Sample/Clear/hash-link convenience; nothing
 * about the actual backend call depends on it).
 */
const SEPARATOR = '<<<<<<< DIFF-CHECKER-SEPARATOR >>>>>>>';

export function serializeTextDiffInput({ original, updated }: TextDiffFields): string {
  return `${original}\n${SEPARATOR}\n${updated}`;
}

export function parseTextDiffInput(raw: string): TextDiffFields {
  const separatorIndex = raw.indexOf(`\n${SEPARATOR}\n`);
  if (separatorIndex === -1) {
    // Tolerant fallback (e.g. DevUtilsPage.tsx's Clear button setting `input` to `''`) — the
    // whole string becomes `original` with `updated` left blank, rather than discarding it.
    return { original: raw, updated: '' };
  }
  return {
    original: raw.slice(0, separatorIndex),
    updated: raw.slice(separatorIndex + SEPARATOR.length + 2),
  };
}
