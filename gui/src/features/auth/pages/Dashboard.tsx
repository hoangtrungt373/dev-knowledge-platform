import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { profileApi } from '../api/profileApi';
import { authService } from '../services/authService';
import { User } from '../types';
import { useNotification } from '@shared/contexts/NotificationContext';
import { useSubmitGuard } from '@shared/hooks/useSubmitGuard';

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

export default function Dashboard(): JSX.Element | null {
  const navigate = useNavigate();
  const { showError, showSuccess } = useNotification();
  const { loading: saving, guard } = useSubmitGuard();

  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [username, setUsername] = useState('');
  const [usernameError, setUsernameError] = useState('');
  const [avatarUploading, setAvatarUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    (async () => {
      try {
        const me = await profileApi.getCurrentUser(showError);
        setUser(me);
        setFirstName(me.firstName ?? '');
        setLastName(me.lastName ?? '');
        setUsername(me.username ?? '');
      } catch (error) {
        if ((error as any)?.status === 401) authService.logout();
      } finally {
        setLoading(false);
      }
    })();
  }, [showError]);

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
    guard(async () => {
      const updated = await profileApi.updateProfile(
        { firstName: firstName.trim(), lastName: lastName.trim(), username: trimmedUsername },
        showError,
      );
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
              Your email address is not verified. Check your inbox for the verification code we sent during registration.
            </Typography>
            <Button
              color="inherit"
              size="small"
              variant="outlined"
              onClick={() => navigate(`/verify-otp?email=${encodeURIComponent(user.email)}`)}
              sx={{ whiteSpace: 'nowrap', flexShrink: 0 }}
            >
              Verify now
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

        {isEditing ? (
          <Grid container spacing={2}>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="First Name"
                value={firstName}
                onChange={e => setFirstName(e.target.value)}
                inputProps={{ maxLength: 255 }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Last Name"
                value={lastName}
                onChange={e => setLastName(e.target.value)}
                inputProps={{ maxLength: 255 }}
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <TextField
                fullWidth
                label="Username"
                value={username}
                onChange={e => {
                  setUsername(e.target.value);
                  setUsernameError('');
                }}
                error={!!usernameError}
                helperText={usernameError || 'Lowercase letters, numbers, and underscores only'}
                inputProps={{ maxLength: 30 }}
              />
            </Grid>
          </Grid>
        ) : (
          <Grid container spacing={3}>
            <Grid item xs={12} sm={6}>
              <InfoRow label="First Name" value={user.firstName} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <InfoRow label="Last Name" value={user.lastName} />
            </Grid>
            <Grid item xs={12} sm={6}>
              <InfoRow
                label="Email"
                value={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <span>{user.email}</span>
                    {user.emailVerified
                      ? <CheckCircleIcon sx={{ fontSize: 16, color: 'success.main' }} />
                      : <CancelOutlinedIcon sx={{ fontSize: 16, color: 'warning.main' }} />
                    }
                    <Typography
                      component="span"
                      variant="body2"
                      color={user.emailVerified ? 'success.main' : 'warning.main'}
                    >
                      {user.emailVerified ? 'Verified' : 'Not verified'}
                    </Typography>
                  </Stack>
                }
              />
            </Grid>
            <Grid item xs={12} sm={6}>
              <InfoRow label="Username" value={`@${user.username}`} />
            </Grid>
          </Grid>
        )}
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