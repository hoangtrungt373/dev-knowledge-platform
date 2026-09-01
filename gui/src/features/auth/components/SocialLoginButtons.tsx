import { Button, Stack } from '@mui/material';
import GoogleIcon from '@mui/icons-material/Google';
import FacebookIcon from '@mui/icons-material/Facebook';
import { PROVIDER_COLORS } from '@shared/constants/colors';
import { OAuthProvider } from '../types';

interface Props {
  onSelect: (provider: OAuthProvider) => void;
}

/**
 * Shared Google/Facebook button pair — `Login.tsx` and `SignUp.tsx` used to duplicate this
 * verbatim (including the per-provider hover-color styling from `PROVIDER_COLORS`).
 */
export default function SocialLoginButtons({ onSelect }: Props): JSX.Element {
  return (
    <Stack spacing={2}>
      <Button
        variant="outlined"
        size="large"
        fullWidth
        startIcon={<GoogleIcon />}
        onClick={() => onSelect('google')}
        sx={{
          borderColor: PROVIDER_COLORS.google.main,
          color: PROVIDER_COLORS.google.main,
          '&:hover': {
            borderColor: PROVIDER_COLORS.google.hover,
            backgroundColor: PROVIDER_COLORS.google.hoverBg,
          },
        }}
      >
        Continue with Google
      </Button>

      <Button
        variant="outlined"
        size="large"
        fullWidth
        startIcon={<FacebookIcon />}
        onClick={() => onSelect('facebook')}
        sx={{
          borderColor: PROVIDER_COLORS.facebook.main,
          color: PROVIDER_COLORS.facebook.main,
          '&:hover': {
            borderColor: PROVIDER_COLORS.facebook.hover,
            backgroundColor: PROVIDER_COLORS.facebook.hoverBg,
          },
        }}
      >
        Continue with Facebook
      </Button>
    </Stack>
  );
}
