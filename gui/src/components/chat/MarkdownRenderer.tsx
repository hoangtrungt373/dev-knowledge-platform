import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Box, useTheme } from '@mui/material';

interface Props {
  content: string;
}

/**
 * Renders markdown with GFM support (tables, strikethrough, task lists) and
 * syntax-highlighted code blocks.  Typography and spacing are tuned to sit
 * comfortably inside a chat message bubble.
 */
export default function MarkdownRenderer({ content }: Props) {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  return (
    <Box
      sx={{
        '& p': { m: 0, mb: 1, lineHeight: 1.65, '&:last-child': { mb: 0 } },
        '& h1,h2,h3,h4,h5,h6': { mt: 1.5, mb: 0.75, fontWeight: 600, lineHeight: 1.3 },
        '& h1': { fontSize: '1.2rem' },
        '& h2': { fontSize: '1.1rem' },
        '& h3,h4,h5,h6': { fontSize: '1rem' },
        '& ul,ol': { pl: 2.5, mb: 1, mt: 0 },
        '& li': { mb: 0.25 },
        '& a': { color: 'primary.main', textDecorationColor: 'primary.main' },
        '& a:hover': { color: 'primary.light' },
        '& blockquote': {
          borderLeft: `3px solid`,
          borderColor: 'divider',
          pl: 1.5,
          ml: 0,
          my: 1,
          color: 'text.secondary',
          fontStyle: 'italic',
        },
        '& table': { borderCollapse: 'collapse', width: '100%', mb: 1 },
        '& th,td': {
          border: `1px solid`,
          borderColor: 'divider',
          px: 1.5,
          py: 0.75,
          fontSize: '0.825rem',
        },
        '& th': { bgcolor: isDark ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.04)', fontWeight: 600 },
        '& code': {
          fontFamily: '"JetBrains Mono", "Fira Code", "Cascadia Code", monospace',
          fontSize: '0.8rem',
          bgcolor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)',
          px: 0.6,
          py: 0.2,
          borderRadius: 0.5,
        },
        '& pre': { m: 0 },
        '& pre code': { bgcolor: 'transparent', p: 0 },
        '& hr': { borderColor: 'divider', my: 1.5 },
      }}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code({ className, children, ...rest }) {
            const match = /language-(\w+)/.exec(className || '');
            if (match) {
              return (
                <SyntaxHighlighter
                  language={match[1]}
                  style={vscDarkPlus}
                  customStyle={{
                    margin: 0,
                    borderRadius: 6,
                    fontSize: '0.8rem',
                    padding: '12px 16px',
                  }}
                  PreTag="div"
                >
                  {String(children).replace(/\n$/, '')}
                </SyntaxHighlighter>
              );
            }
            return (
              <code className={className} {...rest}>
                {children}
              </code>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </Box>
  );
}
