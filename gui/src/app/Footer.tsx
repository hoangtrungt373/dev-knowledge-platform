import * as React from 'react';
import { Box, Container, Divider, Grid, IconButton, Link as MuiLink, Stack, SvgIcon, SvgIconProps, Typography } from '@mui/material';
import { Link as RouterLink, useLocation } from 'react-router-dom';
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined';
import FacebookIcon from '@mui/icons-material/Facebook';

/** No official `@mui/icons-material` glyph exists for TikTok — a minimal inline `SvgIcon` (its
 * well-known "note" mark) rather than pulling in a whole icon-pack dependency for one glyph. */
function TikTokIcon(props: SvgIconProps): JSX.Element {
  return (
    <SvgIcon {...props} viewBox="0 0 24 24">
      <path d="M16.6 5.82c-.9-.86-1.47-2.02-1.6-3.32h-3.03v13.44a2.6 2.6 0 1 1-2.14-2.56v-3.06a5.6 5.6 0 1 0 5.17 5.58V9.4a7.6 7.6 0 0 0 4.6 1.55V7.93a4.6 4.6 0 0 1-3-2.11Z" />
    </SvgIcon>
  );
}

/** Legal-page links (US-facing e-commerce boilerplate) — every `href` is a placeholder `#`
 * (no corresponding page exists yet in this app) per request; swap these for real routes once
 * those pages are actually built rather than deleting this section. */
const LEGAL_LINKS: { label: string; href: string }[] = [
  { label: 'Privacy Policy', href: '#' },
  { label: 'Terms of Service', href: '#' },
  { label: 'Shipping Policy', href: '#' },
  { label: 'Report a Violation', href: '#' },
];

/** Same placeholder-link treatment as LEGAL_LINKS above — no real social presence exists yet. */
const SOCIAL_LINKS: { label: string; href: string; icon: React.ComponentType<SvgIconProps> }[] = [
  { label: 'Facebook', href: '#', icon: FacebookIcon },
  { label: 'TikTok', href: '#', icon: TikTokIcon },
];

/** Internal quick links — real routes (this app already has these pages), unlike the two lists
 * above, so plain react-router `Link`s rather than placeholder `#` anchors. */
const QUICK_LINKS: { label: string; to: string }[] = [
  { label: 'Shop', to: '/shop' },
  { label: 'Cart', to: '/cart' },
  { label: 'Your Orders', to: '/account/orders' },
  { label: 'Your Account', to: '/account/profile' },
];

/**
 * Site-wide footer for the storefront/user-facing shell — Privacy/Terms/Shipping/Report-a-Violation
 * links, social links (Facebook/TikTok), a few internal quick links, and a copyright line, per
 * request ("other necessary info for a practical page footer").
 *
 * <p>Hidden on the same routes {@link "./NavBar"} already hides itself on (`/admin`, `/chat`,
 * `/messages`) — mirrors that component's own `hidden` check exactly, for the same two reasons:
 * `AdminLayout` is its own full-height shell with no footer of its own design, and Chat/Messages
 * are deliberately full-page layouts that already hide `NavBar` for the same "no chrome" reason.
 */
export default function Footer(): JSX.Element | null {
  const location = useLocation();
  const hidden =
    location.pathname.startsWith('/admin') ||
    location.pathname.startsWith('/chat') ||
    location.pathname.startsWith('/messages');

  if (hidden) return null;

  return (
    <Box
      component="footer"
      sx={{
        mt: 'auto',
        bgcolor: 'background.paper',
        borderTop: '1px solid',
        borderColor: 'divider',
      }}
    >
      <Container maxWidth="lg" sx={{ py: 4 }}>
        <Grid container spacing={4}>
          <Grid item xs={12} sm={4}>
            <Typography variant="subtitle1" fontWeight={700} sx={{ mb: 1 }}>
              Dev Knowledge Platform
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2, maxWidth: 280 }}>
              A RAG-powered knowledge platform with a built-in study-project storefront — articles,
              Q&amp;A, and a full shopping experience in one place.
            </Typography>
            <Stack direction="row" spacing={1}>
              {SOCIAL_LINKS.map(({ label, href, icon: Icon }) => (
                <IconButton
                  key={label}
                  component="a"
                  href={href}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={label}
                  size="small"
                  sx={{ border: '1px solid', borderColor: 'divider' }}
                >
                  <Icon fontSize="small" />
                </IconButton>
              ))}
            </Stack>
          </Grid>

          <Grid item xs={6} sm={4} md={3}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
              Quick Links
            </Typography>
            <Stack spacing={1}>
              {QUICK_LINKS.map(({ label, to }) => (
                <MuiLink
                  key={label}
                  component={RouterLink}
                  to={to}
                  variant="body2"
                  color="text.secondary"
                  underline="hover"
                >
                  {label}
                </MuiLink>
              ))}
            </Stack>
          </Grid>

          <Grid item xs={6} sm={4} md={3}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
              Legal
            </Typography>
            <Stack spacing={1}>
              {LEGAL_LINKS.map(({ label, href }) => (
                <MuiLink key={label} href={href} variant="body2" color="text.secondary" underline="hover">
                  {label}
                </MuiLink>
              ))}
            </Stack>
          </Grid>

          <Grid item xs={12} sm={4} md={2}>
            <Typography variant="subtitle2" fontWeight={700} sx={{ mb: 1.5 }}>
              Support
            </Typography>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <EmailOutlinedIcon fontSize="small" color="action" />
              <MuiLink href="mailto:support@example.com" variant="body2" color="text.secondary" underline="hover">
                support@example.com
              </MuiLink>
            </Stack>
          </Grid>
        </Grid>

        <Divider sx={{ my: 3 }} />

        <Typography variant="body2" color="text.secondary" align="center">
          © {new Date().getFullYear()} Dev Knowledge Platform. All rights reserved.
        </Typography>
      </Container>
    </Box>
  );
}
