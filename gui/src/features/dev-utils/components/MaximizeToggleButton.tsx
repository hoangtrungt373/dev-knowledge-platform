import { IconButton, Tooltip } from '@mui/material';
import OpenInFullIcon from '@mui/icons-material/OpenInFullOutlined';
import CloseFullscreenIcon from '@mui/icons-material/CloseFullscreenOutlined';

interface MaximizeToggleButtonProps {
  /** The panel's own title, e.g. "Input"/"Pattern"/"Preview" — used only to build the tooltip
   * text ("Maximize {label}"); the restore-state tooltip ("Restore split view") doesn't need it. */
  label: string;
  maximized: boolean;
  onToggle: () => void;
}

/**
 * The header `IconButton` every panel in this feature renders to maximize/restore itself —
 * `DevUtilToolPanel.tsx`/`HashGeneratorPanel.tsx`/`ColorConverterPanel.tsx`/
 * `UnixTimeConverterPanel.tsx`/`HtmlPreviewPanel.tsx`/`RegExpTesterPanel.tsx`/`TextDiffPanel.tsx`/
 * `Base64ImagePanel.tsx`/`QrCodePanel.tsx`/`LoremIpsumPanel.tsx` each repeated this exact
 * `Tooltip`+`IconButton`+`OpenInFullIcon`/`CloseFullscreenIcon` block once per maximizable card —
 * over 20 near-identical copies across the feature, differing only in which label the tooltip
 * names and which `maximizedPanel === '...'`/`toggleMaximizeX` pair drives it. Each caller still
 * owns its own `usePanelMaximize` state and the small per-card `toggleMaximizeX` callback — this
 * component is purely the presentational button, not a replacement for that state.
 */
export default function MaximizeToggleButton({ label, maximized, onToggle }: MaximizeToggleButtonProps): JSX.Element {
  return (
    <Tooltip title={maximized ? 'Restore split view' : `Maximize ${label}`}>
      <IconButton size="small" onClick={onToggle}>
        {maximized ? <CloseFullscreenIcon fontSize="small" /> : <OpenInFullIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}
