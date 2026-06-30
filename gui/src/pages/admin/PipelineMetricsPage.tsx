import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  CircularProgress,
  Grid,
  IconButton,
  Paper,
  Skeleton,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import AttachMoneyIcon from '@mui/icons-material/AttachMoney';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';
import SpeedIcon from '@mui/icons-material/Speed';
import TokenOutlinedIcon from '@mui/icons-material/TokenOutlined';
import { adminApi } from '../../api/adminApi';
import { MetricsPeriod, PipelineMetricsSummary } from '../../types/admin.types';
import { useNotification } from '../../contexts/NotificationContext';

// ── Formatters ────────────────────────────────────────────────────────────────

function fmtCount(n: number): string {
  return n.toLocaleString();
}

function fmtLatency(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return '—';
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)} s`;
  return `${ms.toLocaleString()} ms`;
}

function fmtCost(cost: number | null | undefined): string {
  if (cost === null || cost === undefined) return '—';
  if (cost < 0.000001) return '<$0.000001';
  return `$${cost.toFixed(6)}`;
}

// ── KPI Card ─────────────────────────────────────────────────────────────────

interface KpiCardProps {
  label: string;
  value: string;
  icon: React.ReactNode;
  iconBg: string;
  subtitle?: string;
  loading: boolean;
}

/**
 * Single metric tile with a coloured icon badge, large value, and optional subtitle.
 * The icon uses a translucent coloured background so it reads well in both themes.
 */
function KpiCard({ label, value, icon, iconBg, subtitle, loading }: KpiCardProps) {
  return (
    <Paper sx={{ p: 2.5, position: 'relative', overflow: 'hidden', height: '100%' }}>
      {/* Icon badge */}
      <Box
        sx={{
          position: 'absolute',
          top: 16, right: 16,
          width: 36, height: 36,
          borderRadius: 1.5,
          bgcolor: iconBg,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        {icon}
      </Box>

      <Stack spacing={0.5} sx={{ pr: 6 }}>
        <Typography variant="caption" color="text.secondary" fontWeight={500} sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}>
          {label}
        </Typography>

        {loading ? (
          <Skeleton variant="text" width={90} height={44} sx={{ transform: 'none' }} />
        ) : (
          <Typography variant="h4" fontWeight={700} sx={{ lineHeight: 1.15 }}>
            {value}
          </Typography>
        )}

        {subtitle && !loading && (
          <Typography variant="caption" color="text.secondary">
            {subtitle}
          </Typography>
        )}
        {subtitle && loading && (
          <Skeleton variant="text" width={60} />
        )}
      </Stack>
    </Paper>
  );
}

// ── Token bar ─────────────────────────────────────────────────────────────────

interface TokenBarProps {
  label: string;
  count: number;
  total: number;
  /** MUI sx-compatible color path, e.g. 'primary.main'. */
  color: string;
}

/** Horizontal proportional bar for one token category. */
function TokenBar({ label, count, total, color }: TokenBarProps) {
  const pct = total > 0 ? (count / total) * 100 : 0;

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="baseline" sx={{ mb: 0.75 }}>
        <Typography variant="body2" color="text.secondary">{label}</Typography>
        <Stack direction="row" spacing={1.5} alignItems="baseline">
          <Typography variant="body2" fontWeight={600}>{fmtCount(count)}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 40, textAlign: 'right' }}>
            {pct.toFixed(1)}%
          </Typography>
        </Stack>
      </Stack>
      <Box sx={{ height: 7, bgcolor: 'action.hover', borderRadius: 1 }}>
        <Box
          sx={{
            width: `${pct}%`,
            height: '100%',
            bgcolor: color,
            borderRadius: 1,
            transition: 'width 0.6s ease',
          }}
        />
      </Box>
    </Box>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

const PERIOD_LABELS: Record<MetricsPeriod, string> = {
  LAST_24H: 'Last 24 h',
  LAST_7_DAYS: 'Last 7 days',
  LAST_30_DAYS: 'Last 30 days',
};

/** Admin analytics page for RAG pipeline cost, latency, and token usage. */
export default function PipelineMetricsPage(): JSX.Element {
  const { showError } = useNotification();
  const [period, setPeriod] = useState<MetricsPeriod>('LAST_7_DAYS');
  const [data, setData] = useState<PipelineMetricsSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await adminApi.getPipelineMetricsSummary(period, showError);
      setData(result);
    } catch {
      // showError already called by httpClient
    } finally {
      setLoading(false);
    }
  }, [period, showError]);

  useEffect(() => { load(); }, [load]);

  // Derived values
  const successful = data ? data.totalRequests - data.abortedRequests : 0;
  const abortRate =
    data && data.totalRequests > 0
      ? `${((data.abortedRequests / data.totalRequests) * 100).toFixed(1)}% abort rate`
      : undefined;

  const totalTokens = data
    ? data.tokenUsage.prompt + data.tokenUsage.completion + data.tokenUsage.embedding
    : 0;

  return (
    <Box sx={{ p: 3, maxWidth: 1000 }}>
      {/* ── Header ── */}
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="flex-start"
        flexWrap="wrap"
        gap={2}
        sx={{ mb: 3 }}
      >
        <Box>
          <Typography variant="h5" fontWeight={700}>Pipeline Metrics</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
            RAG pipeline cost, latency, and token usage
          </Typography>
        </Box>

        <Stack direction="row" spacing={1} alignItems="center">
          <ToggleButtonGroup
            value={period}
            exclusive
            onChange={(_, v: MetricsPeriod) => { if (v) setPeriod(v); }}
            size="small"
          >
            {(Object.keys(PERIOD_LABELS) as MetricsPeriod[]).map(p => (
              <ToggleButton key={p} value={p} sx={{ px: 1.5, fontSize: '0.75rem' }}>
                {PERIOD_LABELS[p]}
              </ToggleButton>
            ))}
          </ToggleButtonGroup>

          <Tooltip title="Refresh">
            <span>
              <IconButton size="small" onClick={load} disabled={loading}>
                {loading
                  ? <CircularProgress size={15} />
                  : <RefreshIcon fontSize="small" />}
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
      </Stack>

      {/* ── KPI cards ── */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="Total Requests"
            value={loading ? '—' : fmtCount(data?.totalRequests ?? 0)}
            icon={<TrendingUpIcon sx={{ fontSize: 20, color: 'primary.main' }} />}
            iconBg="rgba(88,166,255,0.12)"
            loading={loading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="Successful"
            value={loading ? '—' : fmtCount(successful)}
            icon={<CheckCircleOutlineIcon sx={{ fontSize: 20, color: 'success.main' }} />}
            iconBg="rgba(63,185,80,0.12)"
            loading={loading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="Aborted"
            value={loading ? '—' : fmtCount(data?.abortedRequests ?? 0)}
            icon={<ErrorOutlineIcon sx={{ fontSize: 20, color: 'error.main' }} />}
            iconBg="rgba(248,81,73,0.12)"
            subtitle={abortRate}
            loading={loading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="Estimated Cost"
            value={loading ? '—' : fmtCost(data?.estimatedCostUsd)}
            icon={<AttachMoneyIcon sx={{ fontSize: 20, color: 'warning.main' }} />}
            iconBg="rgba(210,153,34,0.12)"
            loading={loading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="P50 Latency"
            value={loading ? '—' : fmtLatency(data?.latencyP50Ms)}
            icon={<TimerOutlinedIcon sx={{ fontSize: 20, color: 'primary.main' }} />}
            iconBg="rgba(88,166,255,0.08)"
            subtitle="Median end-to-end"
            loading={loading}
          />
        </Grid>

        <Grid item xs={12} sm={6} md={4}>
          <KpiCard
            label="P95 Latency"
            value={loading ? '—' : fmtLatency(data?.latencyP95Ms)}
            icon={<SpeedIcon sx={{ fontSize: 20, color: 'secondary.main' }} />}
            iconBg="rgba(63,185,80,0.08)"
            subtitle="95th-percentile end-to-end"
            loading={loading}
          />
        </Grid>
      </Grid>

      {/* ── Token usage ── */}
      <Paper sx={{ p: 2.5 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2.5 }}>
          <Stack direction="row" spacing={1} alignItems="center">
            <TokenOutlinedIcon sx={{ fontSize: 18, color: 'text.secondary' }} />
            <Typography variant="subtitle2" fontWeight={600}>Token Usage</Typography>
          </Stack>

          {!loading && totalTokens > 0 && (
            <Typography variant="body2" color="text.secondary">
              Total&nbsp;
              <Typography component="span" variant="body2" fontWeight={700}>
                {fmtCount(totalTokens)}
              </Typography>
            </Typography>
          )}
        </Stack>

        {loading ? (
          <Stack spacing={2.5}>
            {[0, 1, 2].map(i => (
              <Box key={i}>
                <Stack direction="row" justifyContent="space-between" sx={{ mb: 0.75 }}>
                  <Skeleton variant="text" width={120} />
                  <Skeleton variant="text" width={80} />
                </Stack>
                <Skeleton variant="rounded" height={7} />
              </Box>
            ))}
          </Stack>
        ) : data && totalTokens > 0 ? (
          <Stack spacing={2.5}>
            <TokenBar
              label="Prompt (input to LLM)"
              count={data.tokenUsage.prompt}
              total={totalTokens}
              color="primary.main"
            />
            <TokenBar
              label="Completion (output from LLM)"
              count={data.tokenUsage.completion}
              total={totalTokens}
              color="success.main"
            />
            <TokenBar
              label="Embedding (query + quality check)"
              count={data.tokenUsage.embedding}
              total={totalTokens}
              color="warning.main"
            />
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            No token usage recorded in this period.
          </Typography>
        )}
      </Paper>
    </Box>
  );
}
