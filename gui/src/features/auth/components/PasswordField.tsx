import { useState } from 'react';
import { IconButton, InputAdornment, TextField } from '@mui/material';
import LockIcon from '@mui/icons-material/Lock';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: boolean;
  helperText?: string;
}

/**
 * Password `TextField` with a lock icon and a show/hide toggle — `Login.tsx` (once) and
 * `SignUp.tsx` (twice: password + confirm password) used to duplicate this markup. Owns its own
 * show/hide state internally; the parent only ever needs the plain string value.
 */
export default function PasswordField({ label, value, onChange, error, helperText }: Props): JSX.Element {
  const [visible, setVisible] = useState(false);

  return (
    <TextField
      fullWidth
      label={label}
      type={visible ? 'text' : 'password'}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      error={error}
      helperText={helperText}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <LockIcon color="action" />
          </InputAdornment>
        ),
        endAdornment: (
          <InputAdornment position="end">
            <IconButton onClick={() => setVisible(v => !v)} edge="end" size="small">
              {visible ? <VisibilityOffIcon /> : <VisibilityIcon />}
            </IconButton>
          </InputAdornment>
        ),
      }}
    />
  );
}
