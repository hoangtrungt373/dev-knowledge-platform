import { useCallback, useEffect, useMemo, useState } from 'react';
import { Autocomplete, Box, Button, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopyOutlined';
import CheckIcon from '@mui/icons-material/CheckOutlined';
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';
import SubmitButton from '@shared/components/SubmitButton';
import { useNotification } from '@shared/contexts/NotificationContext';
import { devUtilsApi } from '../api/devUtilsApi';
import { DateTimeResponse, TimestampResponse } from '../types';
import { parseUnixTimeInput, serializeUnixTimeInput } from '../utils/unixTimeInputFormat';
import { listSupportedTimeZones } from '../utils/timeZones';
import { useResizableSplit } from '../hooks/useResizableSplit';
import { usePanelMaximize } from '../hooks/usePanelMaximize';
import PanelHeader from './PanelHeader';
import PanelResizeHandle from './PanelResizeHandle';
import { useCopyFeedback } from '../hooks/useCopyFeedback';

interface UnixTimeConverterPanelProps {
  /** Controlled — same lifted `input` state `DevUtilsPage.tsx` already threads into every other
   * panel, so this operation's headline row (Sample/Clear) keeps working unchanged. `input`
   * doubles as the serialized form of all 4 fields below (see `utils/unixTimeInputFormat.ts`'s
   * own doc comment) — there is no separate local state for the date/time, timestamp, or either
   * zone id field; every one is derived from `input` on every render and writes back through
   * `onInputChange`. */
  input: string;
  onInputChange: (value: string) => void;
  actionLabel: string;
  /** Height (px) computed by `DevUtilsPage.tsx` from the actual viewport — the same value the
   * shared `DevUtilToolPanel`'s Input card and the sidebar both size themselves to. Applied as a
   * `minHeight` floor on both converter cards, the same cosmetic-only "align with the rest of the
   * page, not because this operation's own small, bounded rows ever need to grow" treatment
   * `HashGeneratorPanel.tsx` already establishes for the identical reason. */
  availableHeight: number;
}

/** Best-effort detection of the browser's own IANA zone — never throws (a handful of very old/
 * locked-down browsers don't implement `Intl.DateTimeFormat().resolvedOptions().timeZone`), and
 * this module has no server-side notion of "the caller's own timezone" to fall back on either (a
 * fully public, stateless endpoint — see `dev-utils-service/CLAUDE.md`'s own
 * `service.impl.support.TimeZones` note), so UTC is the deterministic last resort either way. */
function detectBrowserZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';
  } catch {
    return 'UTC';
  }
}

/** A hand-rolled `yyyy-MM-ddTHH:mm:ss` string in the *local* browser clock — exactly the shape
 * `DateTimeToTimestampRequest.dateTime()`'s own Javadoc documents accepting (the same shape an
 * HTML {@code <input type="datetime-local">} already produces), built from `Date`'s own local
 * getters (not `toISOString()`, which is always UTC) so "Use now" fills the field with a value
 * that actually matches whatever zone the adjacent zone-id field already holds. */
function toDateTimeLocalValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

interface LabeledCopyRowProps {
  label: string;
  value: string;
  copied: boolean;
  onCopy: () => void;
}

/** The "label + monospace value + its own Copy button" row both converter cards' own output
 * sections render, one per field — a local helper (not a new shared `components/` file) since
 * both call sites live in this same file; extract only if a third, genuinely different panel
 * ever needs the identical shape, the same "extract on a real 2nd/3rd occurrence, not
 * speculatively" rule this feature's own support-class/hook extractions already follow. */
function LabeledCopyRow({ label, value, copied, onCopy }: LabeledCopyRowProps): JSX.Element {
  return (
    <Stack direction="row" alignItems="center" spacing={1} sx={{ py: 0.5 }}>
      <Typography variant="caption" color="text.secondary" sx={{ width: 110, flexShrink: 0 }}>
        {label}
      </Typography>
      <Typography
        sx={{ flex: 1, minWidth: 0, fontFamily: 'monospace', fontSize: '0.85rem', wordBreak: 'break-word' }}
      >
        {value}
      </Typography>
      <Tooltip title={copied ? 'Copied!' : 'Copy'}>
        <IconButton size="small" onClick={onCopy}>
          {copied ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
        </IconButton>
      </Tooltip>
    </Stack>
  );
}

/**
 * Unix Time Converter's own bespoke layout — a dedicated component, not a mode grafted onto the
 * shared `DevUtilToolPanel.tsx`, per direct request: this operation covers 2 genuinely different
 * directions (Date/Time → Unix, Unix → Date/Time), each with its own 2-field input (a value plus
 * a zone id) and a real multi-field output, none of which fits that component's single-code-
 * editor Input/Output pair. `DevUtilsPage.tsx` renders this in place of `DevUtilToolPanel` for
 * exactly this one operation.
 *
 * <p><b>A live "Now" bar sits above the converter row, entirely client-side — no backend call at
 * all.</b> The current instant is exactly what {@link Date#now} already gives for free; round-
 * tripping it through a request just to ask "what time is it" would only add latency for zero
 * benefit, the same reasoning `Base64ImagePanel.tsx` already establishes for its own
 * `FileReader`-based conversion. Ticks every second via a plain `setInterval` (cleared on
 * unmount); its own "Use now" buttons freeze the live value into the adjacent converter's own
 * input field at the moment of the click, not a live binding — clicking again re-freezes it.
 *
 * <p><b>Both zone-id fields are a searchable dropdown, per a follow-up request</b> — an MUI
 * `Autocomplete` (`freeSolo`, `inputValue`/`onInputChange`-controlled, the same shape
 * `@ecommerce/components/ProductVariantDialog.tsx`'s own "suggested but freely editable" value
 * picker already establishes) listing every IANA zone id the browser's own ICU data knows about
 * ({@link listSupportedTimeZones}, ~400 entries — `Intl.supportedValuesOf('timeZone')` under the
 * hood). `freeSolo` keeps this genuinely optional/free-text underneath the dropdown, not a
 * strict enum picker: the field still accepts (and starts pre-filled with) a zone the list
 * doesn't happen to suggest, and still accepts blank.
 *
 * <p><b>Both zone-id fields are pre-filled with the browser's own detected zone
 * ({@link detectBrowserZone}), not left blank</b> — this module has no persisted user/timezone
 * context of any kind (a fully public, stateless backend), so the browser is the only thing that
 * actually knows the caller's real zone; leaving the field blank would silently default to UTC
 * server-side (see `dev-utils-service/CLAUDE.md`'s own `TimeZones` note), which is rarely what
 * someone converting "their own" date/time actually wants. The detected zone is only ever a
 * pre-filled *placeholder value* (shown via the underlying `TextField`'s own `placeholder`, not
 * forced into `inputValue`), never forced — either field can be edited/cleared freely (clearing
 * it still defaults to UTC, exactly as the backend's own Javadoc documents).
 *
 * <p>Both converter cards get the same resizable-split-plus-maximize/`minHeight`-floor treatment
 * `HashGeneratorPanel.tsx`'s own follow-up already established for a 2-panel, small-and-bounded-
 * result operation like this one — see that component's own doc comment for why (cross-panel
 * visual alignment, not because either side's own content ever needs to grow the way
 * `RegExpTesterPanel.tsx`'s match list or `TextDiffPanel.tsx`'s Diff view can).
 */
export default function UnixTimeConverterPanel({
  input,
  onInputChange,
  actionLabel,
  availableHeight,
}: UnixTimeConverterPanelProps): JSX.Element {
  const { showError } = useNotification();
  const { copiedKey, copy } = useCopyFeedback();
  const browserZone = useMemo(detectBrowserZone, []);
  // `listSupportedTimeZones` itself is already memoized module-level (the browser's own ICU data
  // can't change within a page load) — this `useMemo` just avoids re-invoking that function
  // (a cheap cache lookup, but still a function call) on every render.
  const timeZoneOptions = useMemo(listSupportedTimeZones, []);

  const { dateTime, dateTimeZoneId, timestamp, timestampZoneId } = parseUnixTimeInput(input);

  const updateField = useCallback(
    (field: 'dateTime' | 'dateTimeZoneId' | 'timestamp' | 'timestampZoneId', value: string) => {
      onInputChange(
        serializeUnixTimeInput({ dateTime, dateTimeZoneId, timestamp, timestampZoneId, [field]: value })
      );
    },
    [onInputChange, dateTime, dateTimeZoneId, timestamp, timestampZoneId]
  );

  const [nowMs, setNowMs] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), 1_000);
    return () => clearInterval(id);
  }, []);
  const nowLocalText = useMemo(() => new Date(nowMs).toString(), [nowMs]);
  const nowSeconds = Math.floor(nowMs / 1_000);

  const { maximizedPanel, toggle: toggleMaximize } = usePanelMaximize<'dateToUnix' | 'unixToDate'>();
  const toggleMaximizeDateToUnix = useCallback(() => toggleMaximize('dateToUnix'), [toggleMaximize]);
  const toggleMaximizeUnixToDate = useCallback(() => toggleMaximize('unixToDate'), [toggleMaximize]);

  const {
    rowRef,
    splitPercent,
    resizing,
    minPercent,
    maxPercent,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    handleDoubleClick,
    handleKeyDown,
  } = useResizableSplit({ storageKey: 'devUtilsUnixTimePanelSplitPercent' });

  const [convertingDateToUnix, setConvertingDateToUnix] = useState(false);
  const [timestampResult, setTimestampResult] = useState<TimestampResponse | null>(null);
  const [convertingUnixToDate, setConvertingUnixToDate] = useState(false);
  const [dateTimeResult, setDateTimeResult] = useState<DateTimeResponse | null>(null);

  const handleConvertDateToUnix = useCallback(async () => {
    setConvertingDateToUnix(true);
    try {
      const result = await devUtilsApi.dateTimeToUnix(dateTime, dateTimeZoneId.trim() || browserZone);
      setTimestampResult(result);
    } catch (submitError) {
      setTimestampResult(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not convert this date/time.';
      showError(message);
    } finally {
      setConvertingDateToUnix(false);
    }
  }, [dateTime, dateTimeZoneId, browserZone, showError]);

  const handleConvertUnixToDate = useCallback(async () => {
    setConvertingUnixToDate(true);
    try {
      const result = await devUtilsApi.unixToDateTime(timestamp, timestampZoneId.trim() || browserZone);
      setDateTimeResult(result);
    } catch (submitError) {
      setDateTimeResult(null);
      const message = submitError instanceof Error ? submitError.message : 'Could not convert this timestamp.';
      showError(message);
    } finally {
      setConvertingUnixToDate(false);
    }
  }, [timestamp, timestampZoneId, browserZone, showError]);

  const handleUseNowForDateToUnix = useCallback(() => {
    updateField('dateTime', toDateTimeLocalValue(new Date(nowMs)));
  }, [updateField, nowMs]);

  const handleUseNowForUnixToDate = useCallback(() => {
    updateField('timestamp', String(nowSeconds));
  }, [updateField, nowSeconds]);

  const dateToUnixHidden = maximizedPanel === 'unixToDate';
  const unixToDateHidden = maximizedPanel === 'dateToUnix';

  return (
    <Box>
      <Paper variant="outlined" sx={{ mb: 2 }}>
        <PanelHeader title="Now">
          <Tooltip title={copiedKey === 'now-seconds' ? 'Copied!' : 'Copy Unix seconds'}>
            <IconButton size="small" onClick={() => copy(String(nowSeconds), 'now-seconds')}>
              {copiedKey === 'now-seconds' ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
        </PanelHeader>
        <Stack direction="row" flexWrap="wrap" alignItems="center" spacing={3} sx={{ p: 2 }}>
          <Typography sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>{nowLocalText}</Typography>
          <Typography sx={{ fontFamily: 'monospace', fontSize: '0.85rem', color: 'text.secondary' }}>
            Unix: {nowSeconds}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ ml: 'auto' }}>
            <Button size="small" variant="outlined" onClick={handleUseNowForDateToUnix}>
              Use in Date/Time → Unix
            </Button>
            <Button size="small" variant="outlined" onClick={handleUseNowForUnixToDate}>
              Use in Unix → Date/Time
            </Button>
          </Stack>
        </Stack>
      </Paper>

      {/* `position: 'relative'` anchors PanelResizeHandle; no `gap` — both sides' own flex-basis
          percentages sum to 100%, mirroring every other resizable split in this feature. */}
      <Box ref={rowRef} sx={{ position: 'relative', display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start' }}>
        <Paper
          variant="outlined"
          sx={{
            flex: maximizedPanel === 'dateToUnix' ? '1 1 100%' : `1 1 ${splitPercent}%`,
            minWidth: 320,
            minHeight: availableHeight,
            display: dateToUnixHidden ? 'none' : 'block',
          }}
        >
          <PanelHeader title="Date/Time → Unix">
            <Tooltip title={maximizedPanel === 'dateToUnix' ? 'Restore split view' : 'Maximize Date/Time → Unix'}>
              <IconButton size="small" onClick={toggleMaximizeDateToUnix}>
                {maximizedPanel === 'dateToUnix' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <Stack spacing={2}>
              <TextField
                label="Date/Time"
                type="datetime-local"
                value={dateTime}
                onChange={e => updateField('dateTime', e.target.value)}
                size="small"
                fullWidth


                InputLabelProps={{ shrink: true }}
                inputProps={{ step: 1 }}
              />
              <Autocomplete
                freeSolo
                size="small"
                fullWidth
                options={timeZoneOptions}
                inputValue={dateTimeZoneId}
                onInputChange={(_, newValue) => updateField('dateTimeZoneId', newValue)}
                renderInput={params => (
                  <TextField
                    {...params}
                    label="Time zone"
                    placeholder={browserZone}
                    helperText="An IANA zone id, e.g. Asia/Ho_Chi_Minh — blank defaults to UTC"
                  />
                )}
              />
              <Box>
                <SubmitButton
                  size="small"
                  saving={convertingDateToUnix}
                  label={actionLabel}
                  startIcon={<PlayArrowIcon fontSize="small" />}
                  onClick={handleConvertDateToUnix}
                  disabled={!dateTime.trim()}
                />
              </Box>
              {timestampResult === null ? (
                <Typography variant="body2" color="text.secondary">
                  The Unix timestamp will appear here.
                </Typography>
              ) : (
                <Box>
                  <LabeledCopyRow
                    label="Unix Seconds"
                    value={String(timestampResult.epochSeconds)}
                    copied={copiedKey === 'dtu-seconds'}
                    onCopy={() => copy(String(timestampResult.epochSeconds), 'dtu-seconds')}
                  />
                  <LabeledCopyRow
                    label="Unix Millis"
                    value={String(timestampResult.epochMillis)}
                    copied={copiedKey === 'dtu-millis'}
                    onCopy={() => copy(String(timestampResult.epochMillis), 'dtu-millis')}
                  />
                </Box>
              )}
            </Stack>
          </Box>
        </Paper>

        <PanelResizeHandle
          ariaLabel="Resize Date/Time to Unix and Unix to Date/Time panels"
          splitPercent={splitPercent}
          minPercent={minPercent}
          maxPercent={maxPercent}
          resizing={resizing}
          hidden={maximizedPanel !== null}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onDoubleClick={handleDoubleClick}
          onKeyDown={handleKeyDown}
        />

        <Paper
          variant="outlined"
          sx={{
            flex: maximizedPanel === 'unixToDate' ? '1 1 100%' : `1 1 ${100 - splitPercent}%`,
            minWidth: 320,
            minHeight: availableHeight,
            display: unixToDateHidden ? 'none' : 'block',
          }}
        >
          <PanelHeader title="Unix → Date/Time">
            <Tooltip title={maximizedPanel === 'unixToDate' ? 'Restore split view' : 'Maximize Unix → Date/Time'}>
              <IconButton size="small" onClick={toggleMaximizeUnixToDate}>
                {maximizedPanel === 'unixToDate' ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </PanelHeader>
          <Box sx={{ p: 2 }}>
            <Stack spacing={2}>
              <TextField
                label="Unix timestamp"
                value={timestamp}
                onChange={e => updateField('timestamp', e.target.value)}
                placeholder={String(nowSeconds)}
                size="small"
                fullWidth
                helperText="Seconds or milliseconds — the unit is auto-detected"
                sx={{ '& .MuiInputBase-input': { fontFamily: 'monospace' } }}
              />
              <Autocomplete
                freeSolo
                size="small"
                fullWidth
                options={timeZoneOptions}
                inputValue={timestampZoneId}
                onInputChange={(_, newValue) => updateField('timestampZoneId', newValue)}
                renderInput={params => (
                  <TextField
                    {...params}
                    label="Time zone"
                    placeholder={browserZone}
                    helperText="For the Local row below — blank defaults to UTC"
                  />
                )}
              />
              <Box>
                <SubmitButton
                  size="small"
                  saving={convertingUnixToDate}
                  label={actionLabel}
                  startIcon={<PlayArrowIcon fontSize="small" />}
                  onClick={handleConvertUnixToDate}
                  disabled={!timestamp.trim()}
                />
              </Box>
              {dateTimeResult === null ? (
                <Typography variant="body2" color="text.secondary">
                  Every representation will appear here.
                </Typography>
              ) : (
                <Box>
                  <LabeledCopyRow
                    label={`Local (${timestampZoneId.trim() || browserZone})`}
                    value={dateTimeResult.local}
                    copied={copiedKey === 'utd-local'}
                    onCopy={() => copy(dateTimeResult.local, 'utd-local')}
                  />
                  <LabeledCopyRow
                    label="UTC"
                    value={dateTimeResult.utc}
                    copied={copiedKey === 'utd-utc'}
                    onCopy={() => copy(dateTimeResult.utc, 'utd-utc')}
                  />
                  <LabeledCopyRow
                    label="ISO 8601"
                    value={dateTimeResult.iso8601}
                    copied={copiedKey === 'utd-iso'}
                    onCopy={() => copy(dateTimeResult.iso8601, 'utd-iso')}
                  />
                  <LabeledCopyRow
                    label="RFC 1123"
                    value={dateTimeResult.rfc1123}
                    copied={copiedKey === 'utd-rfc'}
                    onCopy={() => copy(dateTimeResult.rfc1123, 'utd-rfc')}
                  />
                  <LabeledCopyRow
                    label="SQL"
                    value={dateTimeResult.sql}
                    copied={copiedKey === 'utd-sql'}
                    onCopy={() => copy(dateTimeResult.sql, 'utd-sql')}
                  />
                  <LabeledCopyRow
                    label="Relative"
                    value={dateTimeResult.relative}
                    copied={copiedKey === 'utd-relative'}
                    onCopy={() => copy(dateTimeResult.relative, 'utd-relative')}
                  />
                  <LabeledCopyRow
                    label="Day of week"
                    value={dateTimeResult.dayOfWeek}
                    copied={copiedKey === 'utd-dow'}
                    onCopy={() => copy(dateTimeResult.dayOfWeek, 'utd-dow')}
                  />
                  <LabeledCopyRow
                    label="Unix Seconds"
                    value={String(dateTimeResult.epochSeconds)}
                    copied={copiedKey === 'utd-seconds'}
                    onCopy={() => copy(String(dateTimeResult.epochSeconds), 'utd-seconds')}
                  />
                  <LabeledCopyRow
                    label="Unix Millis"
                    value={String(dateTimeResult.epochMillis)}
                    copied={copiedKey === 'utd-millis'}
                    onCopy={() => copy(String(dateTimeResult.epochMillis), 'utd-millis')}
                  />
                </Box>
              )}
            </Stack>
          </Box>
        </Paper>
      </Box>
    </Box>
  );
}
