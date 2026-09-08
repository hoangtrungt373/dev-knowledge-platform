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
import DevUtilSidebarItem from '../components/DevUtilSidebarItem';
import { OPERATION_GROUP_ORDER, OPERATIONS, TabKey, tabFromHash } from '../config/operations';

// A standing preference (like AdminLayout's own sidebar collapse), not per-session UI state, so
// it's persisted to localStorage the same way — see AdminLayout.tsx's own COLLAPSE_STORAGE_KEY.
const SIDEBAR_COLLAPSE_STORAGE_KEY = 'devUtilsSidebarCollapsed';
const SIDEBAR_EXPANDED_WIDTH = 300;
const SIDEBAR_COLLAPSED_WIDTH = 56;
// Fallback for the sidebar's own maxHeight before ResizeObserver's first measurement of the main
// column lands (see mainColumnHeight below) — an initial render has to pick something, and a plain
// `undefined` (no cap at all) would flash the sidebar at its own full, uncapped height for one
// frame before snapping down. A hand-tuned guess, same "eyeballed, not measured" caveat
// DevUtilToolPanel.tsx's own OUTPUT_EMPTY_MIN_HEIGHT already carries — but only ever visible for a
// single frame, never the steady-state value once the observer's callback has fired.
const SIDEBAR_MAX_HEIGHT_FALLBACK = 615;

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
 * <p>**The sidebar's own `maxHeight` is instead a live measurement of the `main` column's real
 * rendered height** (`mainColumnHeight`, kept in sync via a `ResizeObserver` on `mainColumnRef`),
 * per request — not a hand-tuned pixel constant that silently goes stale the moment the headline
 * description wraps to a second line or the Input `TextField`'s `minRows` changes.
 * `SIDEBAR_MAX_HEIGHT_FALLBACK` only covers the single frame before the observer's first callback
 * lands. The tool list scrolls internally (`overflowY: 'auto'`) once it's taller than that
 * measured height, rather than being clipped or growing the whole row.
 *
 * <p>The headline card (category/title/description) also carries **Sample** (fills the input with
 * the operation's own `inputPlaceholder` value) and **Clear** buttons, per request — which is
 * *why* `input` is owned here (`useState`, reset to `''` whenever `tab` changes) and passed down
 * to `DevUtilToolPanel` as a controlled prop, rather than staying local state inside that
 * component: this headline row needs to read/write it directly, and it's a sibling of the panel,
 * not an ancestor, so the state had to move up to their common parent (this component) — the
 * standard "lift state up" fix for two components that both need the same piece of state.
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

  // Live-measures the `main` column's own rendered height (headline card + gap + tool panel) so
  // the sidebar's `maxHeight` below can track it without a hand-tuned pixel constant — see this
  // component's own doc comment for why flexbox's `align-items: stretch` can't do this instead.
  const mainColumnRef = useRef<HTMLDivElement | null>(null);
  const [mainColumnHeight, setMainColumnHeight] = useState<number | null>(null);

  useEffect(() => {
    const node = mainColumnRef.current;
    if (!node) {
      return undefined;
    }
    const observer = new ResizeObserver(entries => {
      const entry = entries[0];
      if (entry) {
        setMainColumnHeight(entry.contentRect.height);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

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
            maxHeight: mainColumnHeight ?? SIDEBAR_MAX_HEIGHT_FALLBACK,
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
                      fontWeight={700}
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

          {/* Keying by operation forces a remount on switch, resetting the panel's own local
              minify/saving/copied state — the same reset a tool switch already caused under the
              old {tab === 'x' && <Panel/>} conditional-rendering shape, just made explicit now
              that one shared JSX call site renders every tool. */}
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
            onSubmit={activeOperation.onSubmit}
          />
        </Box>
      </Box>
    </Box>
  );
}
