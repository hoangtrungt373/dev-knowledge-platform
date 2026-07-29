import { styled } from '@mui/material/styles';
import { Separator } from 'react-resizable-panels';

// react-resizable-panels' Separator is a plain unstyled div (no MUI sx support — it only accepts
// standard HTMLAttributes) — styled via MUI's own styled() (built on @emotion/styled, which this
// project already depends on, but theme-aware — plain @emotion/styled's callback theme param
// doesn't know about MUI's palette/spacing augmentations) rather than introducing a second styling
// approach for one component. A 4px-wide
// hit target (comfortable to grab) with a thin 1px visible line centered inside it via ::after,
// widening/recoloring on hover or while focused (keyboard resize) — this library exposes no
// "currently dragging" data-attribute to hook a third state off, so :active covers the drag case.
const ResizeHandle = styled(Separator)(({ theme }) => ({
  width: 4,
  flexShrink: 0,
  cursor: 'col-resize',
  position: 'relative',
  background: 'transparent',
  outline: 'none',
  '&::after': {
    content: '""',
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: '50%',
    width: 1,
    transform: 'translateX(-50%)',
    backgroundColor: theme.palette.divider,
    transition: 'background-color 0.1s, width 0.1s',
  },
  '&:hover::after, &:active::after, &:focus-visible::after': {
    width: 2,
    backgroundColor: theme.palette.primary.main,
  },
}));

export default ResizeHandle;
