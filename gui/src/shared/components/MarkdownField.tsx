import { useState } from 'react';
import { Box, IconButton, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import MarkdownView from './MarkdownView';

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
  minRows?: number;
  error?: boolean;
  helperText?: string;
  placeholder?: string;
}

export default function MarkdownField({
  label,
  value,
  onChange,
  required,
  minRows = 4,
  error,
  helperText,
  placeholder = 'Supports Markdown — use ` ```java ` for code blocks',
}: Props): JSX.Element {
  const [tab, setTab] = useState<0 | 1>(0);

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 0.5 }}>
        <Typography
          variant="caption"
          fontWeight={600}
          color={error ? 'error.main' : 'text.secondary'}
        >
          {label}{required && ' *'}
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Tabs
            value={tab}
            onChange={(_, v) => setTab(v)}
            sx={{
              minHeight: 28,
              '& .MuiTab-root': { minHeight: 28, py: 0, px: 1.5, fontSize: '0.75rem' },
              '& .MuiTabs-indicator': { height: 2 },
            }}
          >
            <Tab label="Edit" value={0} />
            <Tab label="Preview" value={1} />
          </Tabs>
          <Tooltip title="Markdown guide">
            <IconButton
              size="small"
              component="a"
              href="https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax"
              target="_blank"
              rel="noopener noreferrer"
              sx={{ color: 'text.disabled', '&:hover': { color: 'text.secondary' } }}
            >
              <HelpOutlineIcon sx={{ fontSize: 16 }} />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {tab === 0 ? (
        <TextField
          value={value}
          onChange={e => onChange(e.target.value)}
          multiline
          minRows={minRows}
          fullWidth
          error={error}
          helperText={helperText}
          placeholder={placeholder}
          inputProps={{ style: { fontFamily: 'monospace', fontSize: '0.8125rem' } }}
        />
      ) : (
        <Box
          sx={{
            border: '1px solid',
            borderColor: error ? 'error.main' : 'divider',
            borderRadius: 1,
            minHeight: minRows * 22,
            px: 1.5,
            py: 1,
            overflow: 'auto',
          }}
        >
          {value.trim() ? (
            <MarkdownView content={value} />
          ) : (
            <Typography variant="body2" color="text.disabled" sx={{ fontStyle: 'italic' }}>
              Nothing to preview
            </Typography>
          )}
        </Box>
      )}
    </Box>
  );
}
