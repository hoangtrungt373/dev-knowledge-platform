/**
 * Shared sizing constants for any panel whose Output-like region grows with its own content
 * instead of being pinned to `availableHeight` — `DevUtilToolPanel.tsx`'s own Output card
 * established this "floor at `availableHeight`, grow past it, cap by *lines* rather than an
 * arbitrary pixel number" shape first (so content at or under the cap just grows the box and lets
 * the *page* scroll, while content over the cap scrolls internally instead of growing forever);
 * `RegExpTesterPanel.tsx`'s match list and `TextDiffPanel.tsx`'s Diff view both grow the same way
 * for the same reason, so this got extracted once a real 2nd/3rd occurrence of the identical 3
 * constants showed up, rather than redefining them independently per file.
 *
 * `GROWABLE_PANEL_LINE_HEIGHT_PX` is an eyeballed estimate of a typical monospace/body line's
 * rendered height at this feature's usual font sizes — not measured in a real browser, same
 * caveat every other hand-tuned constant in this feature carries.
 */
export const GROWABLE_PANEL_MAX_LINES = 1000;
export const GROWABLE_PANEL_LINE_HEIGHT_PX = 20;
export const GROWABLE_PANEL_MAX_HEIGHT = GROWABLE_PANEL_MAX_LINES * GROWABLE_PANEL_LINE_HEIGHT_PX;
