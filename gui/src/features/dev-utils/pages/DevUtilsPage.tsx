import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Divider,
  IconButton,
  InputAdornment,
  List,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/SearchOutlined';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesomeOutlined';
import ClearIcon from '@mui/icons-material/ClearOutlined';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeftOutlined';
import ChevronRightIcon from '@mui/icons-material/ChevronRightOutlined';

import { DevUtilError } from '../utils/errorFormatting';
import DevUtilToolPanel from '../components/DevUtilToolPanel';
import HashGeneratorPanel from '../components/HashGeneratorPanel';
import Base64ImagePanel from '../components/Base64ImagePanel';
import DevUtilSidebarItem from '../components/DevUtilSidebarItem';
import { OPERATION_GROUP_ORDER, OPERATIONS, TabKey, tabFromHash } from '../config/operations';

// A standing preference (like AdminLayout's own sidebar collapse), not per-session UI state, so
// it's persisted to localStorage the same way — see AdminLayout.tsx's own COLLAPSE_STORAGE_KEY.
const SIDEBAR_COLLAPSE_STORAGE_KEY = 'devUtilsSidebarCollapsed';
const SIDEBAR_EXPANDED_WIDTH = 300;
const SIDEBAR_COLLAPSED_WIDTH = 56;
// Fallback for the sidebar's own fixed height before its first real measurement lands (see
// sidebarHeight below) — an initial render has to pick something, and a plain `undefined` (no
// height at all) would flash the sidebar at its own natural, shrink-to-fit height for one frame
// before snapping to the real measured one. A hand-tuned guess, same "eyeballed, not measured"
// caveat PANEL_MIN_HEIGHT/PANEL_BOTTOM_GUTTER below carry too — but only ever visible for a single
// frame, never the steady-state value once the first measurement lands.
const SIDEBAR_HEIGHT_FALLBACK = 615;
// The Input/Output panels' own height is viewport-relative, not a fixed pixel constant — see
// panelHeight below. PANEL_MIN_HEIGHT is a floor for a short viewport (a laptop with dev tools
// open, a small window) so the panels never shrink to something unusably small; PANEL_BOTTOM_GUTTER
// is breathing room between the panel's own bottom edge and the viewport's, so it doesn't render
// flush against the window edge. Both are eyeballed, not measured — same caveat
// SIDEBAR_HEIGHT_FALLBACK carries elsewhere in this feature.
const PANEL_MIN_HEIGHT = 360;
const PANEL_BOTTOM_GUTTER = 24;
// Fallback for the single frame before panelHeight's own first real measurement lands — same role
// SIDEBAR_HEIGHT_FALLBACK plays for the sidebar.
const PANEL_HEIGHT_FALLBACK = 500;

/** Each tool has its own route via the URL hash (/dev-utils#json-format, /dev-utils#yaml-to-json,
 * etc.), per request — deep-linkable/bookmarkable/shareable, and the browser back/forward buttons
 * step between tools. Still one route/one page (`App.tsx` only registers `/dev-utils` once) — the
 * hash is client-side-only state React Router already exposes via `useLocation().hash`, not a
 * second-level `<Route>`.
 *
 * <p>The operation catalog itself (`TabKey`/`OPERATIONS`/`tabFromHash`) lives in
 * `config/operations.tsx`, not here — this page owns layout/routing, that file owns "what tools
 * exist and how each one calls its own backend endpoint." Splitting them keeps a future new
 * operation's own diff to that one small, focused file instead of this page's own render logic.
 *
 * <p>Operations render as a left sidebar (a plain `Paper` + `List`, not an MUI `Drawer` — same
 * `AccountLayout.tsx` precedent, chosen to avoid that component's own real, documented `Drawer`
 * bugs) with a search box above the list (per request — filters by label/category/description;
 * client-side only, since the full operation list is always in hand). Each row is rendered by
 * `DevUtilSidebarItem` (own file) — including the `action.selected` background on the active item
 * (per request — same convention `app/NavBar.tsx`'s own `NavButton` already establishes) and the
 * collapsed-sidebar icon-only/`Tooltip` treatment. **The sidebar is collapsible, per request** — a
 * chevron `IconButton` in its own header row toggles `sidebarCollapsed`
 * (`SIDEBAR_EXPANDED_WIDTH`/`SIDEBAR_COLLAPSED_WIDTH`, persisted to `localStorage` via
 * `SIDEBAR_COLLAPSE_STORAGE_KEY` — a standing preference, same "persist across reloads" reasoning
 * `AdminLayout.tsx`'s own `COLLAPSE_STORAGE_KEY` documents, not per-session state); collapsed, the
 * search box hides and the search filter itself is bypassed (`visibleOperations`) rather than
 * possibly showing a filtered list with no visible box to explain or clear it.
 * `alignItems: 'flex-start'` on the row keeps the sidebar sized to its own (short) content instead
 * of stretching to match whichever tool panel is taller, same reasoning `AccountLayout.tsx`
 * documents for the identical layout shape — **deliberately not flexbox's own `align-items:
 * stretch` default**, even though that's the standard "make flex siblings share a height"
 * mechanism (it's what already makes Input/Output match each other inside
 * `DevUtilToolPanel.tsx`, since that row never overrides it): stretch is a *symmetric*
 * relationship (every item ends up the same height, whichever is tallest), but what's wanted here
 * is asymmetric — the sidebar should shrink/scroll to fit the `main` column's height, but a long
 * tool list must never inflate the `main` column (headline card + tool panel) to match *it*.
 * Flexbox has no way to express "only this side defers," so this can't be solved with CSS alone.
 *
 * <p>**The sidebar's own `height` is a live measurement, not a hand-tuned pixel constant** that
 * would silently go stale the moment the headline description wraps to a second line — but it's
 * computed directly from the viewport (`sidebarHeight`, alongside `panelHeight` below, in the same
 * effect), **not** by measuring the main column's own actual rendered DOM height via a
 * `ResizeObserver` the way an earlier version of this did. This is a fixed `height`, **not** a
 * `maxHeight` — a `maxHeight` alone only clamps the upper bound, so whenever the tool list's own
 * natural content is shorter than that height, the sidebar would render at its own shrink-to-fit
 * height instead of actually matching Input (one real, reported bug this went through). The second,
 * separate real bug a DOM-measurement approach ran into: once Output was allowed to grow past
 * `panelHeight` for a long response (see the paragraph below), a `ResizeObserver` watching the whole
 * main column picked that growth up and inflated the sidebar right along with it — the sidebar must
 * stay pinned to the viewport regardless of how tall Output's own content happens to make it, the
 * same as Input. Deriving `sidebarHeight` from the identical top-position measurements
 * `panelHeight` already uses (see that paragraph) sidesteps this entirely, since neither number
 * depends on Output's own rendered size at all. `SIDEBAR_HEIGHT_FALLBACK` only covers the single
 * frame before the first real measurement lands. The tool list scrolls internally
 * (`overflowY: 'auto'`) once it's taller than that height, rather than being clipped or growing the
 * whole row; any extra room below a short list is just blank space inside the card, not a shrink
 * back down to content size.
 *
 * <p>The headline card (category/title/description) also carries **Sample** (fills the input with
 * the operation's own `inputPlaceholder` value) and **Clear** buttons, per request — which is
 * *why* `input` is owned here (`useState`, reset to `''` whenever `tab` changes) and passed down
 * to `DevUtilToolPanel` as a controlled prop, rather than staying local state inside that
 * component: this headline row needs to read/write it directly, and it's a sibling of the panel,
 * not an ancestor, so the state had to move up to their common parent (this component) — the
 * standard "lift state up" fix for two components that both need the same piece of state.
 *
 * <p>**The Input panel's own height is viewport-relative, not a fixed pixel constant**, per
 * request — a payload large enough to matter should get to use whatever vertical room the actual
 * screen has (a 1440p monitor has far more to give than a fixed height would ever use), not a
 * hand-tuned number that's either too short on a big screen or overflowing on a small one.
 * `panelHeight` is computed from `toolPanelRef`'s `getBoundingClientRect().top` (its distance from
 * the viewport's own top — this shifts with the headline card's own height, e.g. a longer
 * description wrapping to a second line) subtracted from `window.innerHeight`, floored at
 * `PANEL_MIN_HEIGHT` and padded by `PANEL_BOTTOM_GUTTER`. Recomputed on a window `resize` (which
 * also fires on a pure reflow-driven width change, e.g. the headline description rewrapping at a
 * narrower width) and whenever `tab`/`sidebarCollapsed` changes (each can shift `toolPanelRef`'s
 * own top position: a different operation's headline is a different height, and collapsing the
 * sidebar widens the main column, which can itself change how many lines the description wraps
 * to). Passed down to `DevUtilToolPanel` as `availableHeight`, which pins the Input `Paper` to
 * exactly that height — see that component's own updated Javadoc.
 *
 * <p>**The Output panel deliberately does *not* use `panelHeight`/`availableHeight` at all,
 * per a follow-up request reverting that part of this design** — it grows with its own content
 * instead, up to a much larger, line-count-based cap (see `DevUtilToolPanel.tsx`'s own
 * `OUTPUT_MAX_HEIGHT`), so a long response can genuinely make Output taller than Input/the
 * sidebar. That's why `sidebarHeight` above is computed directly from the viewport rather than
 * measured off the main column's own rendered height — it must stay pinned to `panelHeight`
 * regardless of how tall Output's own content happens to grow it.
 */
export default function DevUtilsPage(): JSX.Element {
  const location = useLocation();
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabKey>(() => tabFromHash(location.hash));
  const [search, setSearch] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => localStorage.getItem(SIDEBAR_COLLAPSE_STORAGE_KEY) === 'true'
  );
  // Lifted up from DevUtilToolPanel so the headline row's Sample/Clear buttons can set/reset them
  // directly — see DevUtilToolPanel.tsx's own updated Javadoc for the full reasoning.
  const [input, setInput] = useState('');
  const [output, setOutput] = useState<string | null>(null);
  // A submit failure, rendered inline in the Output panel instead of a header notification — see
  // errorFormatting.ts and DevUtilToolPanel.tsx's own updated Javadoc. Mutually exclusive with
  // `output` (a submit always clears one before setting the other).
  const [error, setError] = useState<DevUtilError | null>(null);

  // mainColumnRef anchors the sidebarHeight calc below (its own top === the headline card's top,
  // since the Box it's attached to carries no margin/padding of its own). toolPanelRef wraps just
  // <DevUtilToolPanel/> — its distance from the viewport's own top is what panelHeight needs (the
  // headline card sits above it and shifts this distance whenever its own height changes).
  const mainColumnRef = useRef<HTMLDivElement | null>(null);
  const toolPanelRef = useRef<HTMLDivElement | null>(null);
  const [panelHeight, setPanelHeight] = useState<number>(PANEL_HEIGHT_FALLBACK);
  const [sidebarHeight, setSidebarHeight] = useState<number>(SIDEBAR_HEIGHT_FALLBACK);

  // Viewport-relative height for the Input panel (and, through it, the sidebar) — see this
  // component's own doc comment for the full reasoning. Both panelHeight and sidebarHeight are
  // computed together, from the *same* pair of measurements, on purpose: sidebarHeight must track
  // "headline card height + gap + panelHeight" directly, not the main column's own actual rendered
  // DOM height (a real, reported regression when it did — Output is now allowed to grow past
  // panelHeight for a long response, per a separate follow-up, and a `ResizeObserver` on the whole
  // main column would pick that growth up and inflate the sidebar right along with it, which is
  // exactly the bug being fixed here: the sidebar must stay pinned to the *viewport*, the same as
  // Input, regardless of how tall Output's own content makes it grow).
  useEffect(() => {
    const recompute = () => {
      const mainNode = mainColumnRef.current;
      const panelNode = toolPanelRef.current;
      if (!mainNode || !panelNode) {
        return;
      }
      const mainTop = mainNode.getBoundingClientRect().top;
      const panelTop = panelNode.getBoundingClientRect().top;
      const nextPanelHeight = Math.max(PANEL_MIN_HEIGHT, window.innerHeight - panelTop - PANEL_BOTTOM_GUTTER);
      setPanelHeight(nextPanelHeight);
      setSidebarHeight(panelTop - mainTop + nextPanelHeight);
    };
    recompute();
    window.addEventListener('resize', recompute);
    return () => window.removeEventListener('resize', recompute);
  }, [tab, sidebarCollapsed]);

  // Syncs local state with the hash for the two cases that don't go through selectTab below: a
  // direct deep link (/dev-utils#yaml-to-json) and the browser's own back/forward navigation.
  useEffect(() => {
    setTab(tabFromHash(location.hash));
  }, [location.hash]);

  // Switching tools starts with a blank input/output/error — same reset DevUtilToolPanel's own
  // remount (below) already gives every other piece of its state (minify/saving/copied), just done
  // explicitly here since these three no longer live inside that remounted component.
  useEffect(() => {
    setInput('');
    setOutput(null);
    setError(null);
  }, [tab]);

  const toggleSidebar = useCallback(() => {
    setSidebarCollapsed(prev => {
      const next = !prev;
      localStorage.setItem(SIDEBAR_COLLAPSE_STORAGE_KEY, String(next));
      return next;
    });
  }, []);

  const selectTab = useCallback(
    (newTab: TabKey) => {
      // replace, not push — switching tools shouldn't fill the back-button history with every
      // click; the page's own initial load (or an external deep link) is the meaningful entry.
      navigate(`/dev-utils#${newTab}`, { replace: true });
    },
    [navigate]
  );

  const activeOperation = OPERATIONS.find(op => op.key === tab) ?? OPERATIONS[0];

  const handleUseSample = useCallback(() => {
    setInput(activeOperation.inputPlaceholder);
  }, [activeOperation.inputPlaceholder]);

  const handleClearInput = useCallback(() => {
    setInput('');
    setOutput(null);
    setError(null);
  }, []);

  // Client-side only — the full operation list is always already in hand, no need for a backend
  // round trip to filter a couple dozen known items. Only filters what the sidebar shows; it never
  // changes which tool's panel is currently displayed.
  const query = search.trim().toLowerCase();
  const filteredOperations = query
    ? OPERATIONS.filter(
        op =>
          op.label.toLowerCase().includes(query) ||
          op.category.toLowerCase().includes(query) ||
          op.description.toLowerCase().includes(query)
      )
    : OPERATIONS;
  // The search box itself is hidden while collapsed (no room for it) — show every tool rather than
  // a possibly-filtered list the admin has no way to see the reason for or clear.
  const visibleOperations = sidebarCollapsed ? OPERATIONS : filteredOperations;

  // Buckets the (possibly search-filtered) operation list under each OperationGroupName, in the
  // same fixed order the backend's own OperationGroup enum declares them — a group with zero
  // matching operations (every future group, today) is dropped entirely rather than rendering an
  // empty headline. Built generically off OPERATION_GROUP_ORDER so a future ENCODERS_DECODERS/
  // INSPECTORS/WEB/GENERATORS operation gets its own section headline for free, with no change
  // needed here.
  const groupedVisibleOperations = OPERATION_GROUP_ORDER.map(group => ({
    group,
    operations: visibleOperations.filter(op => op.group === group),
  })).filter(entry => entry.operations.length > 0);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 2 }}>
        DevUtils
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
        <Paper
          variant="outlined"
          sx={{
            width: sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH,
            flexShrink: 0,
            overflowX: 'hidden',
            overflowY: 'auto',
            // A fixed `height`, not a `maxHeight` — a `maxHeight` alone only clamps the *upper*
            // bound, so whenever the tool list's own natural content (today, one "Formatters"
            // headline + 16 short rows) is shorter than this, the sidebar would render at its own
            // shrink-to-fit height instead of matching Input. `height` forces the Paper to that
            // exact size regardless — any extra room below a short list is just blank space inside
            // the card, and `overflowY: 'auto'` still covers the list ever growing taller than it
            // (more groups/operations, a long search result). `sidebarHeight` is computed directly
            // from the viewport (see the effect above), **not** measured off the main column's own
            // rendered DOM height — Output is allowed to grow past `panelHeight` for a long
            // response (see DevUtilToolPanel.tsx), and a DOM measurement would have picked that
            // growth up and inflated the sidebar right along with it (a real, reported regression).
            height: sidebarHeight,
            transition: 'width 0.2s ease',
          }}
        >
          <Stack
            direction="row"
            alignItems="center"
            justifyContent={sidebarCollapsed ? 'center' : 'space-between'}
            sx={{ pl: sidebarCollapsed ? 0 : 1.5, pr: 0.5, py: 0.5 }}
          >
            {!sidebarCollapsed && (
              <Typography
                variant="caption"
                fontWeight={700}
                color="text.secondary"
                sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
              >
                Tools
              </Typography>
            )}
            <Tooltip title={sidebarCollapsed ? 'Expand tools' : 'Collapse tools'}>
              <IconButton size="small" onClick={toggleSidebar}>
                {sidebarCollapsed ? <ChevronRightIcon fontSize="small" /> : <ChevronLeftIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </Stack>
          <Divider />
          {!sidebarCollapsed && (
            <>
              <Box sx={{ p: 1 }}>
                <TextField
                  size="small"
                  fullWidth
                  placeholder="Search tools…"
                  value={search}
                  onChange={e => setSearch(e.target.value)}
                  InputProps={{
                    startAdornment: (
                      <InputAdornment position="start">
                        <SearchIcon fontSize="small" sx={{ color: 'grey.500', mr: 0.5 }} />
                      </InputAdornment>
                    ),
                  }}
                />
              </Box>
              <Divider />
            </>
          )}
          <List dense disablePadding sx={{ py: 0.5 }}>
            {visibleOperations.length === 0 ? (
              !sidebarCollapsed && (
                <Box sx={{ px: 2, py: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    No tools found.
                  </Typography>
                </Box>
              )
            ) : (
              groupedVisibleOperations.map(({ group, operations }) => (
                <Box key={group}>
                  {!sidebarCollapsed && (
                    <Typography
                      variant="caption"
                      fontWeight={400}
                      color="text.secondary"
                      sx={{
                        display: 'block',
                        textTransform: 'uppercase',
                        letterSpacing: 0.5,
                        px: 1.5,
                        pt: 1,
                        pb: 0.5,
                      }}
                    >
                      {group}
                    </Typography>
                  )}
                  {operations.map(op => (
                    <DevUtilSidebarItem
                      key={op.key}
                      operation={op}
                      isSelected={op.key === tab}
                      collapsed={sidebarCollapsed}
                      onSelect={() => selectTab(op.key)}
                    />
                  ))}
                </Box>
              ))
            )}
          </List>
        </Paper>

        <Box component="main" ref={mainColumnRef} sx={{ flex: 1, minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: 2, mb: 2, bgcolor: 'background.paper' }}>
            <Stack direction="row" alignItems="flex-start" justifyContent="space-between" spacing={2}>
              <Box>
                <Typography
                  variant="overline"
                  color="primary.main"
                  fontWeight={700}
                  sx={{ lineHeight: 1.2, display: 'block' }}
                >
                  {activeOperation.category}
                </Typography>
                <Typography variant="h6" fontWeight={700}>
                  {activeOperation.label}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {activeOperation.description}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<AutoAwesomeIcon fontSize="small" />}
                  onClick={handleUseSample}
                >
                  Sample
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<ClearIcon fontSize="small" />}
                  onClick={handleClearInput}
                  disabled={!input && !output && !error}
                >
                  Clear
                </Button>
              </Stack>
            </Stack>
          </Paper>

          {/* toolPanelRef is only for the panelHeight measurement above — it adds no styling of
              its own, so it stays transparent to the layout. Keying the panel itself by operation
              still forces a remount on switch, resetting its own local minify/saving/copied state
              — the same reset a tool switch already caused under the old
              {tab === 'x' && <Panel/>} conditional-rendering shape, just made explicit now that
              one shared JSX call site renders every tool. */}
          <Box ref={toolPanelRef}>
            {/* Some operations render their own bespoke layout instead of the shared
                DevUtilToolPanel — Hash Generator's result (four independent digests) and Base64
                Image's own file-upload/live-preview shape both don't fit that component's plain
                Input/Output editor pair at all. A direct key check per operation, not a lookup
                table/registry — there are only two custom-layout operations today; extend this
                the same way (one more `else if`) if a third one ever needs its own layout too,
                rather than building plugin infrastructure for a hypothetical N ahead of time. */}
            {activeOperation.key === 'hash-generator' ? (
              <HashGeneratorPanel
                key={activeOperation.key}
                input={input}
                onInputChange={setInput}
                output={output}
                onOutputChange={setOutput}
                actionLabel={activeOperation.actionLabel}
                inputPlaceholder={activeOperation.inputPlaceholder}
                // Non-null: every operation except `base64-image` (which never reaches this
                // branch) supplies `onSubmit` — see `OperationConfig.onSubmit`'s own doc comment.
                onSubmit={activeOperation.onSubmit!}
              />
            ) : activeOperation.key === 'base64-image' ? (
              <Base64ImagePanel
                key={activeOperation.key}
                input={input}
                onInputChange={setInput}
                inputPlaceholder={activeOperation.inputPlaceholder}
              />
            ) : (
              <DevUtilToolPanel
                key={activeOperation.key}
                input={input}
                onInputChange={setInput}
                output={output}
                onOutputChange={setOutput}
                error={error}
                onErrorChange={setError}
                actionLabel={activeOperation.actionLabel}
                inputPlaceholder={activeOperation.inputPlaceholder}
                inputFormat={activeOperation.inputFormat}
                outputLanguage={activeOperation.outputLanguage}
                supportsMinify={activeOperation.supportsMinify}
                downloadFileName={activeOperation.downloadFileName}
                // Non-null: every operation rendered through this branch supplies `onSubmit` —
                // see `OperationConfig.onSubmit`'s own doc comment.
                onSubmit={activeOperation.onSubmit!}
                secondaryAction={activeOperation.secondaryAction}
                availableHeight={panelHeight}
              />
            )}
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
