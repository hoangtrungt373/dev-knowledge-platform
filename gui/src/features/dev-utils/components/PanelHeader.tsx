import { ReactNode } from 'react';
import { Stack, Typography } from '@mui/material';

interface PanelHeaderProps {
  title: string;
  /** Spacing between action buttons — `Base64ImagePanel.tsx`'s Preview card uses a tighter `0.5`
   * (two icon-only buttons) where every other card uses the default `1`. */
  actionsSpacing?: number;
  /** Action buttons/icon-buttons rendered right-aligned, e.g. Paste/Copy/Download. Omitted
   * entirely for a card with nothing to act on (`Base64ImagePanel.tsx`'s Upload Image card). */
  children?: ReactNode;
}

/**
 * The bordered "uppercase bold title + optional right-aligned action buttons" header row every
 * card in this feature's Input/Output-shaped panels renders above its own content —
 * `DevUtilToolPanel.tsx` (Input, Output), `HashGeneratorPanel.tsx` (Input), and
 * `Base64ImagePanel.tsx` (Upload Image, Image Data URL, Preview) all used to hand-roll this exact
 * `Stack` markup independently.
 */
export default function PanelHeader({ title, actionsSpacing = 1, children }: PanelHeaderProps): JSX.Element {
  return (
    <Stack
      direction="row"
      alignItems="center"
      justifyContent="space-between"
      sx={{ px: 2, py: 1, borderBottom: 1, borderColor: 'divider' }}
    >
      <Typography variant="subtitle2" fontWeight={700} sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
        {title}
      </Typography>
      {children && (
        <Stack direction="row" spacing={actionsSpacing}>
          {children}
        </Stack>
      )}
    </Stack>
  );
}
