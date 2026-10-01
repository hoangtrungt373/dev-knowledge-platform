import { useState } from 'react';
import { Box, IconButton, Tab, Tabs, TextField, Tooltip, Typography, useTheme } from '@mui/material';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

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
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

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
            '& p': { mt: 0, mb: 1, fontSize: '0.875rem', lineHeight: 1.6 },
            '& code': {
              fontFamily: 'monospace',
              fontSize: '0.8rem',
              bgcolor: 'action.hover',
              px: 0.5,
              py: 0.125,
              borderRadius: 0.5,
            },
            '& pre': { mt: 0, mb: 1, borderRadius: 1, overflow: 'auto' },
            '& ul, & ol': { pl: 2.5, mb: 1, fontSize: '0.875rem' },
            '& li': { mb: 0.25 },
            '& h1': { fontSize: '1.25rem', mt: 1.5, mb: 0.75 },
            '& h2': { fontSize: '1.1rem', mt: 1.25, mb: 0.5 },
            '& h3': { fontSize: '1rem', mt: 1, mb: 0.5 },
            '& blockquote': {
              borderLeft: '3px solid',
              borderColor: 'divider',
              pl: 1.5,
              ml: 0,
              color: 'text.secondary',
              fontStyle: 'italic',
            },
            '& table': { borderCollapse: 'collapse', width: '100%', mb: 1 },
            '& th, & td': { border: '1px solid', borderColor: 'divider', px: 1, py: 0.5, fontSize: '0.8125rem' },
            '& th': { bgcolor: 'action.hover', fontWeight: 700 },
          }}
        >
          {value.trim() ? (
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code({ children, className }) {
                  const match = /language-(\w+)/.exec(className || '');
                  return match ? (
                    <SyntaxHighlighter
                      style={isDark ? oneDark : oneLight}
                      language={match[1]}
                      PreTag="div"
                      customStyle={{ margin: 0, borderRadius: 4, fontSize: '0.8rem' }}
                    >
                      {String(children).replace(/\n$/, '')}
                    </SyntaxHighlighter>
                  ) : (
                    <code className={className}>{children}</code>
                  );
                },
              }}
            >
              {value}
            </ReactMarkdown>
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
