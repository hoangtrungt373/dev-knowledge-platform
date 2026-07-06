import { useState } from 'react';
import { Badge, Box, Tab, Tabs, Typography } from '@mui/material';
import UserSearch from '../components/friends/UserSearch';
import FriendsList from '../components/friends/FriendsList';
import FriendRequestsIncoming from '../components/friends/FriendRequestsIncoming';
import FriendRequestsOutgoing from '../components/friends/FriendRequestsOutgoing';
import BlockedUsersList from '../components/friends/BlockedUsersList';

type TabKey = 'find' | 'friends' | 'requests' | 'sent' | 'blocked';

/**
 * Single-page-with-tabs friend hub, matching facebook.com/friends' shape rather than separate
 * top-level routes per tab (simpler state, no extra layout component, deep-linking not needed yet).
 */
export default function FriendsPage(): JSX.Element {
  const [tab, setTab] = useState<TabKey>('find');
  const [incomingCount, setIncomingCount] = useState(0);

  return (
    <Box sx={{ maxWidth: 720, mx: 'auto', p: 3 }}>
      <Typography variant="h5" fontWeight={700} sx={{ mb: 2 }}>
        Friends
      </Typography>

      <Tabs
        value={tab}
        onChange={(_, v) => setTab(v)}
        sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab value="find" label="Find People" />
        <Tab value="friends" label="Friends" />
        <Tab
          value="requests"
          label={
            <Badge badgeContent={incomingCount} color="error" sx={{ pr: incomingCount > 0 ? 1.5 : 0 }}>
              Requests
            </Badge>
          }
        />
        <Tab value="sent" label="Sent Requests" />
        <Tab value="blocked" label="Blocked" />
      </Tabs>

      {tab === 'find' && <UserSearch />}
      {tab === 'friends' && <FriendsList />}
      {tab === 'requests' && <FriendRequestsIncoming onCountChange={setIncomingCount} />}
      {tab === 'sent' && <FriendRequestsOutgoing />}
      {tab === 'blocked' && <BlockedUsersList />}
    </Box>
  );
}
