import { KeyboardEvent, useRef, useState } from 'react';
import {
  Box,
  IconButton,
  InputAdornment,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import StopIcon from '@mui/icons-material/Stop';

const MAX_CHARS = 2000;

interface Props {
  onSend: (question: string) => void;
  onStop: () => void;
  disabled: boolean;
  streaming: boolean;
}

/**
 * Fixed bottom input bar.
 *
 * Keyboard behaviour:
 *   Enter       → send (if not empty and not streaming)
 *   Shift+Enter → insert newline
 * Max 2000 chars matches the @Size constraint on ChatRequest.question.
 */
export default function ChatInput({ onSend, onStop, disabled, streaming }: Props) {
  const [value, setValue] = useState('');
  const textRef = useRef<HTMLInputElement>(null);

  const handleSend = () => {
    const q = value.trim();
    if (!q || disabled) return;
    onSend(q);
    setValue('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const remaining = MAX_CHARS - value.length;
  const nearLimit = remaining <= 200;

  return (
    <Box
      sx={{
        borderTop: '1px solid',
        borderColor: 'divider',
        px: 2,
        py: 1.5,
        bgcolor: 'background.paper',
      }}
    >
      <TextField
        inputRef={textRef}
        fullWidth
        multiline
        minRows={1}
        maxRows={8}
        value={value}
        onChange={e => setValue(e.target.value.slice(0, MAX_CHARS))}
        onKeyDown={handleKeyDown}
        placeholder="Ask anything… (Enter to send, Shift+Enter for newline)"
        disabled={disabled && !streaming}
        size="small"
        InputProps={{
          sx: {
            borderRadius: 2,
            fontSize: '0.875rem',
            alignItems: 'center',
            pr: 0.5,
          },
          endAdornment: (
            <InputAdornment position="end">
              {streaming ? (
                <Tooltip title="Stop generation">
                  <IconButton size="small" onClick={onStop} color="error">
                    <StopIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              ) : (
                <Tooltip title="Send (Enter)">
                  <span>
                    <IconButton
                      size="small"
                      onClick={handleSend}
                      disabled={!value.trim() || disabled}
                      color="primary"
                    >
                      <SendIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              )}
            </InputAdornment>
          ),
        }}
      />
      {nearLimit && (
        <Typography
          variant="caption"
          sx={{
            display: 'block',
            textAlign: 'right',
            mt: 0.5,
            color: remaining <= 0 ? 'error.main' : 'text.secondary',
          }}
        >
          {remaining} / {MAX_CHARS}
        </Typography>
      )}
    </Box>
  );
}
