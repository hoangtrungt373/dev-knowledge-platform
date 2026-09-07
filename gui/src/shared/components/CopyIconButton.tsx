import { useCallback, useState } from 'react';
import { IconButton, Tooltip } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';

interface CopyIconButtonProps {
  value: string;
  size?: 'small' | 'medium';
}

/** A small "copy this text to the clipboard" icon button — flips to a checkmark for 1.5s after a
 * successful copy, then reverts. No copy-to-clipboard primitive existed anywhere in this app
 * before this; reuse this component rather than a new one wherever that's needed next. */
export default function CopyIconButton({ value, size = 'small' }: CopyIconButtonProps): JSX.Element {
  const [copied, setCopied] = useState(false);

  const handleClick = useCallback(async () => {
    await navigator.clipboard.writeText(value);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }, [value]);

  return (
    <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'}>
      <IconButton size={size} onClick={handleClick}>
        {copied ? <CheckIcon fontSize="small" color="success" /> : <ContentCopyIcon fontSize="small" />}
      </IconButton>
    </Tooltip>
  );
}
