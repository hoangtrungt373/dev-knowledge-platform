import { createTheme, Theme } from '@mui/material/styles';
import { BRAND_COLORS } from './constants/colors';

export type ThemeMode = 'dark' | 'light';

const sharedTypography = {
  fontFamily: '"Inter", "Segoe UI", system-ui, -apple-system, sans-serif',
  h4: { fontWeight: 700 },
  h5: { fontWeight: 700 },
  h6: { fontWeight: 600 },
  body1: { fontSize: '0.875rem' },
  body2: { fontSize: '0.8125rem' },
};

const sharedShape = { borderRadius: 6 };

const sharedComponentDefaults = {
  MuiButton: {
    defaultProps: { size: 'small' as const },
    styleOverrides: {
      root: { textTransform: 'none' as const, fontWeight: 600 },
      sizeMedium: { padding: '5px 14px' },
      sizeLarge: { padding: '7px 18px' },
    },
  },
  MuiTextField: {
    defaultProps: { size: 'small' as const, variant: 'outlined' as const },
  },
  MuiIconButton: {
    defaultProps: { size: 'small' as const },
  },
  MuiChip: {
    defaultProps: { size: 'small' as const },
  },
};

export const darkTheme: Theme = createTheme({
  palette: {
    mode: 'dark',
    background: {
      default: BRAND_COLORS.dark.background,
      paper: BRAND_COLORS.dark.surface,
    },
    primary: { main: BRAND_COLORS.dark.primary },
    secondary: { main: BRAND_COLORS.dark.secondary },
    text: {
      primary: BRAND_COLORS.dark.text,
      secondary: BRAND_COLORS.dark.textSecondary,
    },
    divider: BRAND_COLORS.dark.border,
    error: { main: BRAND_COLORS.dark.error },
    warning: { main: BRAND_COLORS.dark.warning },
    success: { main: BRAND_COLORS.dark.secondary },
  },
  typography: sharedTypography,
  shape: sharedShape,
  components: {
    ...sharedComponentDefaults,
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: BRAND_COLORS.dark.background },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: BRAND_COLORS.dark.surface,
          borderBottom: `1px solid ${BRAND_COLORS.dark.border}`,
          boxShadow: 'none',
          color: BRAND_COLORS.dark.text,
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: `1px solid ${BRAND_COLORS.dark.border}`,
          boxShadow: 'none',
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: BRAND_COLORS.dark.border,
          },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: BRAND_COLORS.dark.textSecondary,
          },
        },
      },
    },
    MuiDivider: {
      styleOverrides: {
        root: { borderColor: BRAND_COLORS.dark.border },
      },
    },
  },
});

export const lightTheme: Theme = createTheme({
  palette: {
    mode: 'light',
    background: {
      default: BRAND_COLORS.light.background,
      paper: BRAND_COLORS.light.surface,
    },
    primary: { main: BRAND_COLORS.light.primary },
    secondary: { main: BRAND_COLORS.light.secondary },
    text: {
      primary: BRAND_COLORS.light.text,
      secondary: BRAND_COLORS.light.textSecondary,
    },
    divider: BRAND_COLORS.light.border,
    error: { main: BRAND_COLORS.light.error },
    warning: { main: BRAND_COLORS.light.warning },
    success: { main: BRAND_COLORS.light.secondary },
  },
  typography: sharedTypography,
  shape: sharedShape,
  components: {
    ...sharedComponentDefaults,
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundColor: BRAND_COLORS.light.background },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: BRAND_COLORS.light.surface,
          borderBottom: `1px solid ${BRAND_COLORS.light.border}`,
          boxShadow: 'none',
          color: BRAND_COLORS.light.text,
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          border: `1px solid ${BRAND_COLORS.light.border}`,
          boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
        },
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: BRAND_COLORS.light.border,
          },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: BRAND_COLORS.light.textSecondary,
          },
        },
      },
    },
    MuiDivider: {
      styleOverrides: {
        root: { borderColor: BRAND_COLORS.light.border },
      },
    },
  },
});
