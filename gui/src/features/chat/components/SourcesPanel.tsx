import { useState } from 'react';
import {
  Box,
  Chip,
  Collapse,
  Divider,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ArticleIcon from '@mui/icons-material/Article';
import QuizIcon from '@mui/icons-material/Quiz';
import { RagSource } from '../types';

interface Props {
  sources: RagSource[];
}

function sourceTypeLabel(type: string): string {
  switch (type) {
    case 'QUESTION_ANSWER': return 'Question';
    case 'ARTICLE': return 'Article';
    case 'BLOG_POST': return 'Blog';
    default: return type;
  }
}

function SourceIcon({ type }: { type: string }) {
  return type === 'QUESTION_ANSWER'
    ? <QuizIcon sx={{ fontSize: 14 }} />
    : <ArticleIcon sx={{ fontSize: 14 }} />;
}

function similarityColor(score: number): string {
  if (score >= 0.9) return 'success.main';
  if (score >= 0.8) return 'primary.main';
  return 'text.secondary';
}

/** Collapsible panel that lists retrieved RAG source chunks below an AI message. */
export default function SourcesPanel({ sources }: Props) {
  const [open, setOpen] = useState(false);

  if (sources.length === 0) return null;

  return (
    <Box sx={{ mt: 1.5 }}>
      <Divider sx={{ mb: 1 }} />

      {/* Toggle button */}
      <Stack
        direction="row"
        spacing={0.5}
        alignItems="center"
        onClick={() => setOpen(v => !v)}
        sx={{ cursor: 'pointer', width: 'fit-content', userSelect: 'none' }}
      >
        <Typography variant="caption" color="text.secondary" fontWeight={500}>
          {sources.length} {sources.length === 1 ? 'source' : 'sources'}
        </Typography>
        {open ? (
          <ExpandLessIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
        ) : (
          <ExpandMoreIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
        )}
      </Stack>

      <Collapse in={open}>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {sources.map((src, i) => (
            <Box
              key={i}
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1,
                p: 1.25,
                bgcolor: 'background.default',
              }}
            >
              {/* Header row */}
              <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" sx={{ mb: 0.5 }}>
                <Chip
                  size="small"
                  icon={<SourceIcon type={src.sourceType} />}
                  label={sourceTypeLabel(src.sourceType)}
                  variant="outlined"
                  sx={{ fontSize: '0.7rem', height: 20 }}
                />
                <Tooltip title="Cosine similarity score" placement="top">
                  <Typography
                    variant="caption"
                    fontWeight={600}
                    sx={{ color: similarityColor(src.similarity) }}
                  >
                    {Math.round(src.similarity * 100)}%
                  </Typography>
                </Tooltip>
              </Stack>

              {/* Title */}
              <Typography variant="body2" fontWeight={600} sx={{ mb: 0.5, lineHeight: 1.3 }}>
                {src.title}
              </Typography>

              {/* Chunk excerpt */}
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: '-webkit-box',
                  WebkitLineClamp: 3,
                  WebkitBoxOrient: 'vertical',
                  overflow: 'hidden',
                  lineHeight: 1.5,
                }}
              >
                {src.chunkText}
              </Typography>
            </Box>
          ))}
        </Stack>
      </Collapse>
    </Box>
  );
}
