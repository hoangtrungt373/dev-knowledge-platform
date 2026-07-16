import { KeyboardEvent, useState } from 'react';
import { Box, IconButton, TextField } from '@mui/material';
import SendIcon from '@mui/icons-material/Send';

interface Props {
  disabled?: boolean;
  onSend: (content: string) => void;
}

/**
 * Text-only composer — no attachment button. MessageAttachmentRequest's own Javadoc says the
 * upload endpoint doesn't exist yet server-side, so there's nothing to wire up this phase.
 * Sends and clears immediately; the sent message renders once the backend echoes it back over
 * the socket, same as any other incoming message (see useDmThread).
 */
export default function MessageComposer({ disabled, onSend }: Props): JSX.Element {
  const [value, setValue] = useState('');

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Box sx={{ display: 'flex', gap: 1, p: 2, borderTop: 1, borderColor: 'divider' }}>
      <TextField
        fullWidth
        multiline
        maxRows={4}
        size="small"
        placeholder={disabled ? 'Connecting…' : 'Type a message…'}
        value={value}
        onChange={e => setValue(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
      />
      <IconButton color="primary" onClick={handleSend} disabled={disabled || !value.trim()}>
        <SendIcon />
      </IconButton>
    </Box>
  );
}
