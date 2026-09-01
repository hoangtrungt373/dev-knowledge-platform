import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import MarkEmailReadIcon from '@mui/icons-material/MarkEmailRead';
import EditIcon from '@mui/icons-material/Edit';
import SaveIcon from '@mui/icons-material/Save';
import CancelIcon from '@mui/icons-material/Cancel';
import GoogleIcon from '@mui/icons-material/Google';
import FacebookIcon from '@mui/icons-material/Facebook';
import LockIcon from '@mui/icons-material/Lock';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import { authApi } from '../api/authApi';
import { profileApi } from '../api/profileApi';
import { authService } from '../services/authService';
import { User } from '../types';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';
import { decodeJwtPayload } from '@shared/utils/jwt';

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="body1" fontWeight={500}>
        {value || <Typography component="span" variant="body1" color="text.disabled">—</Typography>}
      </Typography>
    </Box>
  );
}

function ProviderIcon({ provider }: { provider: string }) {
  if (provider === 'GOOGLE') return <GoogleIcon sx={{ fontSize: 16, color: '#db4437' }} />;
  if (provider === 'FACEBOOK') return <FacebookIcon sx={{ fontSize: 16, color: '#1877f2' }} />;
  return <LockIcon sx={{ fontSize: 16 }} />;
}

function formatDate(iso?: string) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
}

// Applied to the Personal Information fields while not editing — same TextField, same box size,
// just styled to look like plain text so the Paper's height never changes between view/edit.
const readOnlyFieldSx = {
  '& .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
  '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: 'transparent' },
  '& .MuiInputBase-input': { cursor: 'default' },
} as const;

export default function ProfilePage(): JSX.Element | null {
  const { showError, showSuccess } = useNotification();
  const [searchParams, setSearchParams] = useSearchParams();
  const { loading: saving, guard } = useSubmitGuard();

  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [usernameError, setUsernameError] = useState('');
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [resendingVerification, setResendingVerification] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  // This effect calls authService.refreshAccessToken() below on a claim mismatch — a real token
  // rotation, not just an idempotent GET, so it can't safely run twice. StrictMode's dev-mode
  // double-invoke would otherwise fire it twice back to back; a second concurrent refresh could
  // hit an already-rotated-out refresh token and fail. Same guard AuthCallback.tsx/
  // AdminAuthCallback.tsx use for their own one-time-use PKCE code exchange.
  const hasFetchedRef = useRef(false);

  useEffect(() => {
    if (hasFetchedRef.current) return;
    hasFetchedRef.current = true;

    (async () => {
      try {
        const me = await profileApi.getCurrentUser(showError);
        setUser(me);
        setFirstName(me.firstName ?? '');
        setLastName(me.lastName ?? '');
        setUsername(me.username ?? '');

        // A brand-new Google/Facebook login gets JIT-provisioned server-side with a derived
        // username that differs from Keycloak's own default (identity-service's
        // UserServiceImpl.findOrCreateFromKeycloak renames it away from username==email on first
        // sight) — but the access token this very request was authenticated with was minted
        // *before* that rename, so its preferred_username claim still says the old value. Left
        // alone, the next authenticated call anywhere in the app would see that stale claim and
        // get JIT-synced back to it, silently reverting the rename (the same staleness class
        // handleSave's own refreshAccessToken call already guards against for a manual edit).
        // Comparing the claim against what this response just said settles it before that can
        // happen, regardless of which page happens to make the first authenticated call.
        const accessToken = authService.getAccessToken();
        if (accessToken) {
          try {
            const { preferred_username } = decodeJwtPayload<{ preferred_username?: string }>(accessToken);
            if (preferred_username && preferred_username !== me.username) {
              await authService.refreshAccessToken();
            }
          } catch {
            // Best-effort — a decode failure just means the next natural token refresh catches up.
          }
        }
      } catch (error) {
        if ((error as any)?.status === 401) authService.logout();
      } finally {
        setLoading(false);
      }
    })();
  }, [showError]);

  // Landed here (still logged in, GuestRoute bounced /login?emailVerified=true straight through)
  // from identity-service's sendVerifyEmail redirect — same one-time confirmation toast Login.tsx
  // shows for the logged-out case. Strip the param so refreshing doesn't re-show it.
  useEffect(() => {
    if (searchParams.get('emailVerified') === 'true') {
      showSuccess('Your email has been verified!');
      setSearchParams(params => {
        params.delete('emailVerified');
        return params;
      }, { replace: true });
    }
  }, [searchParams, setSearchParams, showSuccess]);

  // Clears the email-verification banner without forcing a full re-login. Verification status is
  // a JWT claim baked into the access token at issuance time, so a plain reload keeps showing the
  // stale (unverified) value — Keycloak's own action-token link can't push a change into an
  // already-open tab. Refreshing gives a real refresh_token grant a chance to pick up the new
  // claim as soon as possible, without waiting for the current access token to actually expire.
  // Runs once immediately (identity-service's sendVerifyEmail redirects back to /dashboard after
  // the Keycloak confirmation click, which is often a brand-new tab/page load — one that never
  // fires visibilitychange on its own) and again on every future tab-refocus (the case where the
  // link was opened in a separate tab and the user comes back to this already-open one).
  // Silent/best-effort — a failed check here just means the next trigger tries again.
  useEffect(() => {
    if (!user || user.emailVerified) return;

    const checkVerification = async () => {
      const refreshed = await authService.refreshAccessToken();
      if (!refreshed) return;
      try {
        const me = await profileApi.getCurrentUser();
        setUser(me);
      } catch {
        // Silent — next trigger retries.
      }
    };

    checkVerification();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        checkVerification();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [user?.emailVerified]);

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      showError('Only image files are allowed');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showError('Avatar must not exceed 5 MB');
      return;
    }

    setAvatarUploading(true);
    try {
      const updated = await profileApi.uploadAvatar(file, showError);
      setUser(updated);
      showSuccess('Avatar updated successfully');
    } finally {
      setAvatarUploading(false);
      // Reset so the same file can be re-selected
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const handleResendVerification = async () => {
    setResendingVerification(true);
    try {
      await authApi.resendVerificationEmail(showError);
      showSuccess('Verification email sent — check your inbox');
    } finally {
      setResendingVerification(false);
    }
  };

  const handleEdit = () => {
    setFirstName(user?.firstName ?? '');
    setLastName(user?.lastName ?? '');
    setUsername(user?.username ?? '');
    setUsernameError('');
    setIsEditing(true);
  };

  const handleCancel = () => setIsEditing(false);

  const handleSave = () => {
    const trimmedUsername = username.trim();
    if (trimmedUsername.length < 3) {
      setUsernameError('Username must be at least 3 characters');
      return;
    }
    if (trimmedUsername.length > 30) {
      setUsernameError('Username must be at most 30 characters');
      return;
    }
    if (!/^[a-z0-9_]+$/.test(trimmedUsername)) {
      setUsernameError('Lowercase letters, numbers, and underscores only');
      return;
    }
    setUsernameError('');
    const usernameChanged = trimmedUsername !== user?.username;
    guard(async () => {
      const updated = await profileApi.updateProfile(
        { firstName: firstName.trim(), lastName: lastName.trim(), username: trimmedUsername },
        showError,
      );
      // A changed username is renamed in Keycloak too (see identity-service's UserServiceImpl),
      // but the currently-held access token's own preferred_username claim was stamped at
      // issuance and won't reflect it until a fresh token is issued — same staleness the
      // email-verification banner already works around. Refresh now so the next request (even a
      // plain page reload) doesn't get JIT-synced back to the old Keycloak-side username.
      if (usernameChanged) await authService.refreshAccessToken();
      setUser(updated);
      setIsEditing(false);
      showSuccess('Profile updated successfully');
    });
  };

  if (loading) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" minHeight="60vh">
        <CircularProgress />
      </Box>
    );
  }

  if (!user) return null;

  const displayName = user.firstName
    ? `${user.firstName}${user.lastName ? ' ' + user.lastName : ''}`
    : user.username;

  const initials = user.firstName
    ? `${user.firstName[0]}${user.lastName ? user.lastName[0] : ''}`.toUpperCase()
    : user.username[0].toUpperCase();

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto', px: 2, py: 4 }}>

      {/* Email verification banner */}
      {!user.emailVerified && (
        <Alert
          severity="warning"
          icon={<MarkEmailReadIcon />}
          sx={{ mb: 2, alignItems: 'center' }}
        >
          <Stack direction="row" spacing={2} alignItems="center">
            <Typography variant="body2">
              Your email address is not verified. Check your inbox for the verification link we sent.
            </Typography>
            <Button
              color="inherit"
              size="small"
              variant="outlined"
              disabled={resendingVerification}
              onClick={handleResendVerification}
              sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
            >
              {resendingVerification ? 'Sending…' : 'Resend email'}
            </Button>
          </Stack>
        </Alert>
      )}

      {/* Profile Header */}
      <Paper sx={{ p: 3, mb: 2 }}>
        <Stack direction="row" spacing={3} alignItems="center">
          {/* Hidden file input */}
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            style={{ display: 'none' }}
            onChange={handleAvatarChange}
          />

          {/* Avatar with camera overlay */}
          <Tooltip title="Change avatar" placement="bottom">
            <Box
              sx={{ position: 'relative', width: 80, height: 80, cursor: 'pointer' }}
              onClick={() => !avatarUploading && fileInputRef.current?.click()}
            >
              <Avatar
                src={user.profilePicture}
                alt={displayName}
                sx={{ width: 80, height: 80, fontSize: '1.75rem' }}
              >
                {initials}
              </Avatar>

              {avatarUploading ? (
                <Box
                  sx={{
                    position: 'absolute', inset: 0, borderRadius: '50%',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: 'rgba(0,0,0,0.45)',
                  }}
                >
                  <CircularProgress size={28} sx={{ color: 'white' }} />
                </Box>
              ) : (
                <Box
                  sx={{
                    position: 'absolute', inset: 0, borderRadius: '50%',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    bgcolor: 'rgba(0,0,0,0.45)',
                    opacity: 0,
                    transition: 'opacity 0.2s',
                    '&:hover': { opacity: 1 },
                  }}
                >
                  <PhotoCameraIcon sx={{ color: 'white', fontSize: 26 }} />
                </Box>
              )}
            </Box>
          </Tooltip>

          <Box flex={1}>
            <Typography variant="h5" fontWeight={700}>{displayName}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              @{user.username}
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap">
              <Chip
                size="small"
                label={user.status}
                sx={{
                  bgcolor: user.status === 'ONLINE' ? 'success.main' : 'action.disabledBackground',
                  color: user.status === 'ONLINE' ? 'success.contrastText' : 'text.secondary',
                  fontWeight: 600,
                }}
              />
              {user.role && (
                <Chip size="small" label={user.role} variant="outlined" sx={{ fontWeight: 600 }} />
              )}
              <Chip
                size="small"
                label={
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <ProviderIcon provider={user.provider} />
                    <span>{user.provider.charAt(0) + user.provider.slice(1).toLowerCase()}</span>
                  </Stack>
                }
                variant="outlined"
              />
            </Stack>
          </Box>

          {!isEditing ? (
            <Button variant="outlined" startIcon={<EditIcon />} onClick={handleEdit}>
              Edit Profile
            </Button>
          ) : (
            <Stack direction="row" spacing={1}>
              <Button variant="contained" startIcon={<SaveIcon />} onClick={handleSave} disabled={saving}>
                {saving ? <CircularProgress size={16} color="inherit" /> : 'Save'}
              </Button>
              <Button variant="outlined" startIcon={<CancelIcon />} onClick={handleCancel} disabled={saving}>
                Cancel
              </Button>
            </Stack>
          )}
        </Stack>
      </Paper>

      {/* Personal Information */}
      <Paper sx={{ p: 3, mb: 2 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Personal Information</Typography>
        <Divider sx={{ mb: 3 }} />

        <Grid container spacing={3}>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              size="small"
              label="First Name"
              placeholder="—"
              value={isEditing ? firstName : (user.firstName ?? '')}
              onChange={e => setFirstName(e.target.value)}
              inputProps={{ maxLength: 255 }}
              InputProps={{ readOnly: !isEditing }}
              sx={!isEditing ? readOnlyFieldSx : undefined}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              size="small"
              label="Last Name"
              placeholder="—"
              value={isEditing ? lastName : (user.lastName ?? '')}
              onChange={e => setLastName(e.target.value)}
              inputProps={{ maxLength: 255 }}
              InputProps={{ readOnly: !isEditing }}
              sx={!isEditing ? readOnlyFieldSx : undefined}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              size="small"
              label="Email"
              value={user.email}
              InputProps={{
                readOnly: true,
                endAdornment: (
                  <Stack direction="row" spacing={0.5} alignItems="center" sx={{ flexShrink: 0, pl: 1 }}>
                    {user.emailVerified
                      ? <CheckCircleIcon sx={{ fontSize: 16, color: 'success.main' }} />
                      : <CancelOutlinedIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                    }
                    <Typography
                      variant="caption"
                      color={user.emailVerified ? 'success.main' : 'warning.main'}
                      sx={{ whiteSpace: 'nowrap' }}
                    >
                      {user.emailVerified ? 'Verified' : 'Not verified'}
                    </Typography>
                  </Stack>
                ),
              }}
              sx={readOnlyFieldSx}
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <TextField
              fullWidth
              size="small"
              label="Username"
              value={isEditing ? username : `@${user.username}`}
              onChange={e => {
                setUsername(e.target.value);
                setUsernameError('');
              }}
              error={!!usernameError}
              helperText={usernameError || 'Lowercase letters, numbers, and underscores only'}
              inputProps={{ maxLength: 30 }}
              InputProps={{ readOnly: !isEditing }}
              sx={!isEditing ? readOnlyFieldSx : undefined}
            />
          </Grid>
        </Grid>
      </Paper>

      {/* Account Details */}
      <Paper sx={{ p: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>Account Details</Typography>
        <Divider sx={{ mb: 3 }} />
        <Grid container spacing={3}>
          <Grid item xs={12} sm={6}>
            <InfoRow
              label="Sign-in method"
              value={
                <Chip
                  size="small"
                  label={
                    <Stack direction="row" spacing={0.5} alignItems="center">
                      <ProviderIcon provider={user.provider} />
                      <span>{user.provider.charAt(0) + user.provider.slice(1).toLowerCase()}</span>
                    </Stack>
                  }
                  variant="outlined"
                />
              }
            />
          </Grid>
          <Grid item xs={12} sm={6}>
            <InfoRow label="Role" value={user.role} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <InfoRow label="Member since" value={formatDate(user.createdAt)} />
          </Grid>
          <Grid item xs={12} sm={6}>
            <InfoRow label="Last updated" value={formatDate(user.lastModified)} />
          </Grid>
        </Grid>
      </Paper>

    </Box>
  );
}