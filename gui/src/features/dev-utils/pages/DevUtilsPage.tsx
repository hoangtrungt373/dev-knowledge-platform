import { SyntheticEvent, useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import { useNotification } from '@shared/contexts/NotificationContext';
import { devUtilsApi } from '../api/devUtilsApi';
import DevUtilToolPanel from '../components/DevUtilToolPanel';

type TabKey = 'json-format' | 'yaml-to-json' | 'json-to-yaml' | 'html-beautify';

const TAB_KEYS: TabKey[] = ['json-format', 'yaml-to-json', 'json-to-yaml', 'html-beautify'];
const DEFAULT_TAB: TabKey = 'json-format';

function tabFromHash(hash: string): TabKey {
  const key = hash.replace(/^#/, '');
  return (TAB_KEYS as string[]).includes(key) ? (key as TabKey) : DEFAULT_TAB;
}

/** Each tool has its own route via the URL hash (/dev-utils#json-format, /dev-utils#yaml-to-json,
 * etc.), per request — deep-linkable/bookmarkable/shareable, and the browser back/forward buttons
 * step between tabs. Still one route/one page (`App.tsx` only registers `/dev-utils` once) — the
 * hash is client-side-only state React Router already exposes via `useLocation().hash`, not a
 * second-level `<Route>`. */
export default function DevUtilsPage(): JSX.Element {
  const { showError } = useNotification();
  const location = useLocation();
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabKey>(() => tabFromHash(location.hash));

  // Syncs local state with the hash for the two cases that don't go through handleTabChange below:
  // a direct deep link (/dev-utils#yaml-to-json) and the browser's own back/forward navigation.
  useEffect(() => {
    setTab(tabFromHash(location.hash));
  }, [location.hash]);

  const handleTabChange = useCallback(
    (_event: SyntheticEvent, newTab: TabKey) => {
      // replace, not push — switching tabs shouldn't fill the back-button history with every
      // click; the page's own initial load (or an external deep link) is the meaningful entry.
      navigate(`/dev-utils#${newTab}`, { replace: true });
    },
    [navigate]
  );

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 2 }}>
        Developer Utilities
      </Typography>

      <Tabs value={tab} onChange={handleTabChange} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="json-format" label="JSON Format" />
        <Tab value="yaml-to-json" label="YAML → JSON" />
        <Tab value="json-to-yaml" label="JSON → YAML" />
        <Tab value="html-beautify" label="HTML Beautify" />
      </Tabs>

      {tab === 'json-format' && (
        <DevUtilToolPanel
          actionLabel="Format"
          inputLabel="JSON input"
          inputPlaceholder='{"foo": "bar"}'
          outputLanguage="json"
          supportsMinify
          onSubmit={(input, minify) => devUtilsApi.formatJson(input, minify, showError)}
        />
      )}
      {tab === 'yaml-to-json' && (
        <DevUtilToolPanel
          actionLabel="Convert"
          inputLabel="YAML input"
          inputPlaceholder="foo: bar"
          outputLanguage="json"
          supportsMinify
          onSubmit={(input, minify) => devUtilsApi.yamlToJson(input, minify, showError)}
        />
      )}
      {tab === 'json-to-yaml' && (
        <DevUtilToolPanel
          actionLabel="Convert"
          inputLabel="JSON input"
          inputPlaceholder='{"foo": "bar"}'
          outputLanguage="yaml"
          supportsMinify={false}
          onSubmit={input => devUtilsApi.jsonToYaml(input, showError)}
        />
      )}
      {tab === 'html-beautify' && (
        <DevUtilToolPanel
          actionLabel="Beautify"
          inputLabel="HTML input"
          inputPlaceholder="<div><p>hello</p></div>"
          outputLanguage="markup"
          supportsMinify
          onSubmit={(input, minify) => devUtilsApi.beautifyHtml(input, minify, showError)}
        />
      )}
    </Box>
  );
}
