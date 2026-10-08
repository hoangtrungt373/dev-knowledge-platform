import { Box, SxProps, Theme, useTheme } from '@mui/material';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface Props {
  content: string;
  sx?: SxProps<Theme>;
}

/**
 * Renders Markdown (GitHub-flavored: tables, strikethrough, task lists) with syntax-highlighted
 * fenced code blocks. Extracted from `MarkdownField`'s Preview tab so an author's preview and the
 * published page (e.g. a practice problem's description) render the same text identically.
 * `@chat`'s `MarkdownRenderer` stays separate on purpose — its spacing is tuned for chat bubbles.
 */
export default function MarkdownView({ content, sx }: Props): JSX.Element {
  const theme = useTheme();
  const isDark = theme.palette.mode === 'dark';

  return (
    <Box
      sx={[
        {
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
          // A fenced block without a language renders as <pre><code> — give it the same frame.
          '& pre > code': { display: 'block', p: 1.5, bgcolor: 'action.hover' },
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
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
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
        {content}
      </ReactMarkdown>
    </Box>
  );
}
