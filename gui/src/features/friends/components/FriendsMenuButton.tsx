import { useState } from 'react';
import { Button, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import BlockIcon from '@mui/icons-material/Block';

interface Props {
  onUnfriend: () => void;
  onBlock: () => void;
  disabled?: boolean;
}

/**
 * The Facebook-style "Friends ▾" dropdown (Unfriend / Block) — shared by FriendsList rows and
 * RelationshipActionButton's FRIENDS case.
 */
export default function FriendsMenuButton({ onUnfriend, onBlock, disabled }: Props): JSX.Element {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  return (
    <>
      <Button
        variant="outlined"
        size="small"
        color="inherit"
        startIcon={<CheckIcon fontSize="small" />}
        endIcon={<ExpandMoreIcon fontSize="small" />}
        onClick={e => setAnchorEl(e.currentTarget)}
        disabled={disabled}
      >
        Friends
      </Button>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        <MenuItem onClick={() => { setAnchorEl(null); onUnfriend(); }}>
          <ListItemIcon><PersonRemoveIcon fontSize="small" /></ListItemIcon>
          <ListItemText>Unfriend</ListItemText>
        </MenuItem>
        <MenuItem onClick={() => { setAnchorEl(null); onBlock(); }} sx={{ color: 'error.main' }}>
          <ListItemIcon><BlockIcon fontSize="small" color="error" /></ListItemIcon>
          <ListItemText>Block</ListItemText>
        </MenuItem>
      </Menu>
    </>
  );
}
