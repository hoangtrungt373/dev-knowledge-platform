import { Chip } from '@mui/material';
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined';

/** Below these thresholds the chip escalates color, mirroring `utils/stock.ts`'s own low-stock
 * warning-threshold convention (shared wording/thresholds so this can't drift per call site). */
const WARNING_THRESHOLD_MS = 10 * 60 * 1000;
const CRITICAL_THRESHOLD_MS = 5 * 60 * 1000;

function formatCountdown(remainingMs: number): string {
  const totalSeconds = Math.ceil(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

interface Props {
  remainingMs: number;
}

/**
 * A live mm:ss "time left to pay" chip — purely presentational (the actual ticking and the
 * call-`reconcilePayment`-on-expiry side effect both live in `usePaymentCountdown`, this component
 * just renders whatever `remainingMs` that hook returns). Escalates to `warning`/`error` as the
 * deadline nears, same threshold-escalation idea `utils/stock.ts`'s `isLowStock` already
 * establishes for stock levels.
 */
export default function PaymentCountdown({ remainingMs }: Props): JSX.Element {
  const color = remainingMs <= CRITICAL_THRESHOLD_MS ? 'error' : remainingMs <= WARNING_THRESHOLD_MS ? 'warning' : 'default';
  return (
    <Chip
      size="small"
      icon={<TimerOutlinedIcon fontSize="small" />}
      label={`Complete payment within ${formatCountdown(remainingMs)}`}
      color={color}
    />
  );
}
