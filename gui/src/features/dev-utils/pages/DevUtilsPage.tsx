import { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Box,
  Button,
  Divider,
  IconButton,
  InputAdornment,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import SearchIcon from '@mui/icons-material/Search';
import DataObjectIcon from '@mui/icons-material/DataObject';
import SyncAltIcon from '@mui/icons-material/SyncAlt';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import AutoFixHighIcon from '@mui/icons-material/AutoFixHigh';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ClearIcon from '@mui/icons-material/Clear';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CssIcon from '@mui/icons-material/Css';
import StyleIcon from '@mui/icons-material/Style';
import ColorLensIcon from '@mui/icons-material/ColorLens';
import JavascriptIcon from '@mui/icons-material/Javascript';
import IntegrationInstructionsIcon from '@mui/icons-material/IntegrationInstructions';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import { devUtilsApi } from '../api/devUtilsApi';
import { DevUtilsResponse } from '../types';
import { DevUtilError } from '../utils/errorFormatting';
import DevUtilToolPanel from '../components/DevUtilToolPanel';

type TabKey =
  | 'json-format'
  | 'yaml-to-json'
  | 'json-to-yaml'
  | 'html-beautify'
  | 'css-beautify'
  | 'less-beautify'
  | 'scss-beautify'
  | 'js-beautify'
  | 'erb-beautify'
  | 'xml-beautify';

const TAB_KEYS: TabKey[] = [
  'json-format',
  'yaml-to-json',
  'json-to-yaml',
  'html-beautify',
  'css-beautify',
  'less-beautify',
  'scss-beautify',
  'js-beautify',
  'erb-beautify',
  'xml-beautify',
];
const DEFAULT_TAB: TabKey = 'json-format';

// A standing preference (like AdminLayout's own sidebar collapse), not per-session UI state, so
// it's persisted to localStorage the same way — see AdminLayout.tsx's own COLLAPSE_STORAGE_KEY.
const SIDEBAR_COLLAPSE_STORAGE_KEY = 'devUtilsSidebarCollapsed';
const SIDEBAR_EXPANDED_WIDTH = 240;
const SIDEBAR_COLLAPSED_WIDTH = 56;

function tabFromHash(hash: string): TabKey {
  const key = hash.replace(/^#/, '');
  return (TAB_KEYS as string[]).includes(key) ? (key as TabKey) : DEFAULT_TAB;
}

interface OperationConfig {
  key: TabKey;
  /** Sidebar group label, also shown as the eyebrow line above the Input/Output panels — e.g.
   * "Formatters" vs. "Converters". Purely descriptive today (the sidebar list itself stays flat,
   * not grouped into sections) — group it visually too if the operation count grows enough to
   * warrant it. */
  category: string;
  label: string;
  /** One-line summary shown above the Input/Output panels, under the operation's own title. */
  description: string;
  icon: JSX.Element;
  actionLabel: string;
  /** Doubles as both the empty-textarea ghost text and the value the headline card's Sample
   * button fills in — unified into one field per request, after `sampleInput` had briefly existed
   * as a separate, richer field (see gui/CLAUDE.md's dev-utils section for that reversal's own
   * history). A realistic, mixed-type example, not a minimal one, since it now has to do both
   * jobs at once. */
  inputPlaceholder: string;
  /** What format the *input* box holds — 'json' for json-format/json-to-yaml, 'yaml' for
   * yaml-to-json, 'html' for html-beautify, and one literal per new operation below. Drives
   * `errorFormatting.ts#buildDevUtilError`'s choice between a client-side `JSON.parse`
   * re-derivation (for 'json' only) and a best-effort cleanup of the backend's own message
   * (everything else) — in practice that fallback only ever actually renders anything for 'yaml'/
   * 'xml', the two operations with a real backend invalid-input error path; 'html'/'css'/'less'/
   * 'scss'/'js'/'erb' can never fail a submit at all (see each one's own backend Javadoc), so this
   * field is otherwise inert for them, kept only so every operation still declares an honest,
   * specific value rather than reusing an unrelated one. */
  inputFormat: 'json' | 'yaml' | 'html' | 'css' | 'less' | 'scss' | 'js' | 'erb' | 'xml';
  /** Prism language for the output syntax highlighter: 'json' | 'yaml' | 'markup' (HTML) | 'css' |
   * 'less' | 'scss' | 'javascript' | 'erb' | 'xml'. */
  outputLanguage: string;
  supportsMinify: boolean;
  /** Filename offered by the Output panel's Download button. */
  downloadFileName: string;
  onSubmit: (input: string, minify: boolean) => Promise<DevUtilsResponse>;
}

/** Each tool has its own route via the URL hash (/dev-utils#json-format, /dev-utils#yaml-to-json,
 * etc.), per request — deep-linkable/bookmarkable/shareable, and the browser back/forward buttons
 * step between tools. Still one route/one page (`App.tsx` only registers `/dev-utils` once) — the
 * hash is client-side-only state React Router already exposes via `useLocation().hash`, not a
 * second-level `<Route>`.
 *
 * <p>Operations render as a left sidebar (a plain `Paper` + `List`, not an MUI `Drawer` — same
 * `AccountLayout.tsx` precedent, chosen to avoid that component's own real, documented `Drawer`
 * bugs) with a search box above the list (per request — filters by label/category/description;
 * client-side only, since the full operation list is always in hand) and an explicit
 * `action.selected` background on the active item (per request — same convention `app/NavBar.tsx`'s
 * own `NavButton` already establishes for "is this the active route," rather than relying on
 * `ListItemButton`'s own default `selected` styling alone). **The sidebar is collapsible, per
 * request** — a chevron `IconButton` in its own header row toggles `sidebarCollapsed`
 * (`SIDEBAR_EXPANDED_WIDTH`/`SIDEBAR_COLLAPSED_WIDTH`, persisted to `localStorage` via
 * `SIDEBAR_COLLAPSE_STORAGE_KEY` — a standing preference, same "persist across reloads" reasoning
 * `AdminLayout.tsx`'s own `COLLAPSE_STORAGE_KEY` documents, not per-session state); collapsed, the
 * search box and item labels hide (icon-only rows, each wrapped in a `Tooltip` carrying the label —
 * same shape `AdminLayout.tsx`'s own collapsed sidebar uses) and the search filter itself is
 * bypassed (`visibleOperations`) rather than possibly showing a filtered list with no visible box to
 * explain or clear it. `alignItems: 'flex-start'` on the row
 * keeps the sidebar sized to its own (short) content instead of stretching to match whichever tool
 * panel is taller, same reasoning `AccountLayout.tsx` documents for the identical layout shape.
 *
 * <p>The headline card (category/title/description) also carries **Sample** (fills the input with
 * the operation's own `inputPlaceholder` value — unified back into one field, see that field's own
 * doc comment for the history) and **Clear** buttons, per request — which is *why*
 * `input` is owned here (`useState`, reset to `''` whenever `tab` changes) and passed down to
 * `DevUtilToolPanel` as a controlled prop, rather than staying local state inside that component:
 * this headline row needs to read/write it directly, and it's a sibling of the panel, not an
 * ancestor, so the state had to move up to their common parent (this component) — the standard
 * "lift state up" fix for two components that both need the same piece of state.
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

  const operations: OperationConfig[] = [
    {
      key: 'json-format',
      category: 'Formatters',
      label: 'JSON Format/Validate',
      description: 'Beautify, minify, and validate JSON',
      icon: <DataObjectIcon fontSize="small" />,
      actionLabel: 'Format',
      inputPlaceholder: '{"project":"Vui Coding","online":true,"tools":["JSON","Base64","JWT"],"stars":128}',
      inputFormat: 'json',
      outputLanguage: 'json',
      supportsMinify: true,
      downloadFileName: 'formatted.json',
      onSubmit: (input, minify) => devUtilsApi.formatJson(input, minify),
    },
    {
      key: 'html-beautify',
      category: 'Formatters',
      label: 'HTML Beautify',
      description: 'Beautify or minify HTML markup',
      icon: <AutoFixHighIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        '<div class="card"><h2>Vui Coding</h2><p>Online: <strong>true</strong></p><ul><li>JSON</li><li>Base64</li><li>JWT</li></ul></div>',
      inputFormat: 'html',
      outputLanguage: 'markup',
      supportsMinify: true,
      downloadFileName: 'beautified.html',
      onSubmit: (input, minify) => devUtilsApi.beautifyHtml(input, minify),
    },
    {
      key: 'css-beautify',
      category: 'Formatters',
      label: 'CSS Beautify/Minify',
      description: 'Beautify or minify CSS stylesheets',
      icon: <CssIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        '.card{background:#fff;padding:16px;}.card h2{color:#333;font-size:20px;}/* Vui Coding */',
      inputFormat: 'css',
      outputLanguage: 'css',
      supportsMinify: true,
      downloadFileName: 'beautified.css',
      onSubmit: (input, minify) => devUtilsApi.beautifyCss(input, minify),
    },
    {
      key: 'less-beautify',
      category: 'Formatters',
      label: 'LESS Beautify/Minify',
      description: 'Beautify or minify LESS stylesheets',
      icon: <StyleIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        '@primary: #333; // Vui Coding\n.card{background:#fff;padding:16px;h2{color:@primary;font-size:20px;}}',
      inputFormat: 'less',
      outputLanguage: 'less',
      supportsMinify: true,
      downloadFileName: 'beautified.less',
      onSubmit: (input, minify) => devUtilsApi.beautifyLess(input, minify),
    },
    {
      key: 'scss-beautify',
      category: 'Formatters',
      label: 'SCSS Beautify/Minify',
      description: 'Beautify or minify SCSS stylesheets',
      icon: <ColorLensIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        '$primary: #333; // Vui Coding\n.card{background:#fff;padding:16px;h2{color:$primary;font-size:20px;}}',
      inputFormat: 'scss',
      outputLanguage: 'scss',
      supportsMinify: true,
      downloadFileName: 'beautified.scss',
      onSubmit: (input, minify) => devUtilsApi.beautifyScss(input, minify),
    },
    {
      key: 'js-beautify',
      category: 'Formatters',
      label: 'JS Beautify/Minify',
      description: 'Beautify or minify JavaScript code',
      icon: <JavascriptIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        'function describe(project){if(project.online){return project.name+" has "+project.stars+" stars";}return null;}',
      inputFormat: 'js',
      outputLanguage: 'javascript',
      supportsMinify: true,
      downloadFileName: 'beautified.js',
      onSubmit: (input, minify) => devUtilsApi.beautifyJs(input, minify),
    },
    {
      key: 'erb-beautify',
      category: 'Formatters',
      label: 'ERB Beautify/Minify',
      description: 'Beautify or minify ERB (Embedded RuBy) templates',
      icon: <IntegrationInstructionsIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder: '<div class="card"><h2><%= project.name %></h2><% if project.online %><p>Online</p><% end %></div>',
      inputFormat: 'erb',
      outputLanguage: 'erb',
      supportsMinify: true,
      downloadFileName: 'beautified.erb',
      onSubmit: (input, minify) => devUtilsApi.beautifyErb(input, minify),
    },
    {
      key: 'xml-beautify',
      category: 'Formatters',
      label: 'XML Beautify/Minify',
      description: 'Validate, beautify, or minify XML documents',
      icon: <AccountTreeIcon fontSize="small" />,
      actionLabel: 'Beautify',
      inputPlaceholder:
        '<project><name>Vui Coding</name><online>true</online><tools><tool>JSON</tool><tool>Base64</tool></tools></project>',
      inputFormat: 'xml',
      outputLanguage: 'xml',
      supportsMinify: true,
      downloadFileName: 'beautified.xml',
      onSubmit: (input, minify) => devUtilsApi.beautifyXml(input, minify),
    },
    {
      key: 'yaml-to-json',
      category: 'Converters',
      label: 'YAML to JSON',
      description: 'Convert YAML documents into JSON',
      icon: <SyncAltIcon fontSize="small" />,
      actionLabel: 'Convert',
      inputPlaceholder: 'project: Vui Coding\nonline: true\ntools:\n  - JSON\n  - Base64\n  - JWT\nstars: 128\n',
      inputFormat: 'yaml',
      outputLanguage: 'json',
      supportsMinify: true,
      downloadFileName: 'converted.json',
      onSubmit: (input, minify) => devUtilsApi.yamlToJson(input, minify),
    },
    {
      key: 'json-to-yaml',
      category: 'Converters',
      label: 'JSON to YAML',
      description: 'Convert JSON documents into YAML',
      icon: <SwapHorizIcon fontSize="small" />,
      actionLabel: 'Convert',
      inputPlaceholder: '{"project":"Vui Coding","online":true,"tools":["JSON","Base64","JWT"],"stars":128}',
      inputFormat: 'json',
      outputLanguage: 'yaml',
      // No minify option here — jackson-dataformat-yaml has no single-line/flow-style toggle, so
      // devUtilsApi.jsonToYaml doesn't even accept the parameter (see its own comment).
      supportsMinify: false,
      downloadFileName: 'converted.yaml',
      onSubmit: input => devUtilsApi.jsonToYaml(input),
    },
  ];

  const activeOperation = operations.find(op => op.key === tab) ?? operations[0];

  const handleUseSample = useCallback(() => {
    setInput(activeOperation.inputPlaceholder);
  }, [activeOperation.inputPlaceholder]);

  const handleClearInput = useCallback(() => {
    setInput('');
    setOutput(null);
    setError(null);
  }, []);

  // Client-side only — the full operation list is always already in hand, no need for a backend
  // round trip to filter 4 (or even a few dozen, if this grows) known items. Only filters what the
  // sidebar shows; it never changes which tool's panel is currently displayed. Plain recomputation
  // each render, not useMemo — the list is tiny (4 items) and rebuilt every render regardless, so
  // memoizing on `search` alone would either need `operations` in the dep array (defeating the
  // memoization, since that array is a fresh reference every render anyway) or silently ignore it.
  const query = search.trim().toLowerCase();
  const filteredOperations = query
    ? operations.filter(
        op =>
          op.label.toLowerCase().includes(query) ||
          op.category.toLowerCase().includes(query) ||
          op.description.toLowerCase().includes(query)
      )
    : operations;
  // The search box itself is hidden while collapsed (no room for it) — show every tool rather than
  // a possibly-filtered list the admin has no way to see the reason for or clear.
  const visibleOperations = sidebarCollapsed ? operations : filteredOperations;

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 2 }}>
        DevUtils
      </Typography>

      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 3 }}>
        <Paper
          variant="outlined"
          sx={{
            width: sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH,
            flexShrink: 0,
            overflow: 'hidden',
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
                        <SearchIcon fontSize="small" />
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
              visibleOperations.map(op => {
                const isSelected = op.key === tab;
                const itemButton = (
                  <ListItemButton
                    key={op.key}
                    selected={isSelected}
                    onClick={() => selectTab(op.key)}
                    sx={{
                      borderRadius: 1,
                      mx: 0.5,
                      mb: 0.25,
                      justifyContent: sidebarCollapsed ? 'center' : 'flex-start',
                      px: sidebarCollapsed ? 1 : 2,
                      // Target `&.Mui-selected` explicitly, not a plain `bgcolor` on the root —
                      // ListItemButton's own baked-in selected-state rule
                      // (`&.Mui-selected { backgroundColor: action.selected }`) has *higher* CSS
                      // specificity (root class + Mui-selected class) than a plain `bgcolor` on
                      // the component's own root class alone, so it was silently winning over
                      // this override regardless of the `isSelected` conditional already choosing
                      // the right value in JS — matching MUI's own selector exactly is what makes
                      // this override actually take effect.
                      '&.Mui-selected': {
                        bgcolor: theme => alpha(theme.palette.primary.main, 0.16),
                      },
                      '&.Mui-selected:hover': {
                        bgcolor: theme => alpha(theme.palette.primary.main, 0.24),
                      },
                      '&:hover': {
                        bgcolor: 'action.hover',
                      },
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: sidebarCollapsed ? 0 : 32, justifyContent: 'center' }}>
                      {op.icon}
                    </ListItemIcon>
                    {/* fontWeight is fixed regardless of selection — only the background above
                        distinguishes the selected item now, per request. */}
                    {!sidebarCollapsed && (
                      <ListItemText
                        primary={op.label}
                        primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
                      />
                    )}
                  </ListItemButton>
                );
                // Collapsed sidebar has no room for the label — a tooltip carries it instead, same
                // "icon-only row, label in a Tooltip" shape AdminLayout's own collapsed sidebar uses.
                return sidebarCollapsed ? (
                  <Tooltip key={op.key} title={op.label} placement="right">
                    {itemButton}
                  </Tooltip>
                ) : (
                  itemButton
                );
              })
            )}
          </List>
        </Paper>

        <Box component="main" sx={{ flex: 1, minWidth: 0 }}>
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
