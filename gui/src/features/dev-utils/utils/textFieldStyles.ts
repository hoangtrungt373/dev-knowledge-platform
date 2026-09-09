import { SxProps, Theme } from '@mui/material';

/**
 * Hides a `TextField`'s own default outlined-variant border — for a `TextField` nested inside a
 * card that already draws its own `Paper` border, where the `TextField`'s own border reads as a
 * literal "box inside a box." Hidden under all three states explicitly (default/hover/focus each
 * carry their own selector for this element, so a bare base-selector-only override loses on hover/
 * focus — a real MUI specificity gotcha this feature has hit more than once).
 *
 * <p>Spread this into a `TextField`'s own `sx` object (after any other overrides, e.g. the
 * monospace font) rather than passing it as `sx` directly — `HashGeneratorPanel.tsx`'s Input box
 * and `Base64ImagePanel.tsx`'s Image Data URL box both also set `& .MuiInputBase-input` alongside
 * it.
 */
export const HIDDEN_TEXT_FIELD_OUTLINE_SX: SxProps<Theme> = {
  '& .MuiOutlinedInput-notchedOutline': { border: 'none' },
  '&:hover .MuiOutlinedInput-notchedOutline': { border: 'none' },
  '& .Mui-focused .MuiOutlinedInput-notchedOutline': { border: 'none' },
};
